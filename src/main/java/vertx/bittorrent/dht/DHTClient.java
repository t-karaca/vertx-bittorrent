package vertx.bittorrent.dht;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import vertx.bittorrent.Peer;
import vertx.bittorrent.TrackerResponse;
import vertx.bittorrent.dht.exception.DHTErrorException;
import vertx.bittorrent.dht.messages.DHTAnnouncePeerQuery;
import vertx.bittorrent.dht.messages.DHTAnnouncePeerResponse;
import vertx.bittorrent.dht.messages.DHTFindNodeQuery;
import vertx.bittorrent.dht.messages.DHTFindNodeResponse;
import vertx.bittorrent.dht.messages.DHTGetPeersQuery;
import vertx.bittorrent.dht.messages.DHTGetPeersResponse;
import vertx.bittorrent.dht.messages.DHTPingQuery;
import vertx.bittorrent.dht.messages.DHTPingResponse;

@Slf4j
public class DHTClient {

    private final Vertx vertx;

    private final DHTProtocolHandler protocolHandler;
    private final DHTTokenManager tokenManager;
    private final DHTRoutingTable routingTable;

    private final long timerId;
    private long saveTableTimerId;

    private boolean tableUpdated = false;

    private List<DHTLookup> activeLookups = new ArrayList<>();

    public DHTClient(Vertx vertx) {
        log.info("Starting dht client");

        this.vertx = vertx;
        this.protocolHandler = new DHTProtocolHandler(vertx);
        this.tokenManager = new DHTTokenManager(vertx);

        File dhtFile = new File("dht.json");
        if (dhtFile.exists()) {
            log.info("Using existing routing table from dht.json");

            try (var inputStream = new FileInputStream(dhtFile)) {
                routingTable = DHTRoutingTable.parse(inputStream);
                log.info("Parsed file");
            } catch (IOException e) {
                log.error("Could not read routing table from file:", e);
                throw new RuntimeException(e);
            }
        } else {
            log.info("Creating new routing table");

            routingTable = new DHTRoutingTable();
        }

        routingTable.onUpdated(v -> {
            tableUpdated = true;
        });

        log.info("Node id: {}", routingTable.getNodeId().toHexString());

        protocolHandler.onQuery(DHTPingQuery.class, (sender, query) -> {
            routingTable.refreshNode(query.getNodeId(), sender);

            return DHTPingResponse.builder().nodeId(routingTable.getNodeId()).build();
        });

        protocolHandler.onQuery(DHTFindNodeQuery.class, (sender, query) -> {
            routingTable.refreshNode(query.getNodeId(), sender);

            List<DHTNode> nodes = routingTable
                    .findNodeById(query.getTarget())
                    .map(Collections::singletonList)
                    .orElseGet(() -> routingTable.findClosestNodesForId(query.getTarget()));

            byte[] bytes = nodesToBytes(nodes);

            return DHTFindNodeResponse.builder()
                    .nodeId(routingTable.getNodeId())
                    .nodes(bytes)
                    .build();
        });

        protocolHandler.onQuery(DHTGetPeersQuery.class, (sender, query) -> {
            routingTable.refreshNode(query.getNodeId(), sender);

            byte[] infoHash = query.getInfoHash();

            var peers = routingTable.findPeersForTorrent(infoHash);

            byte[] nodes = null;

            if (peers.isEmpty()) {
                nodes = nodesToBytes(routingTable.findClosestNodesForId(query.getNodeId()));
            }

            byte[] token = tokenManager.createToken(sender);

            return DHTGetPeersResponse.builder()
                    .nodeId(routingTable.getNodeId())
                    .values(peers)
                    .nodes(nodes)
                    .token(token)
                    .build();
        });

        protocolHandler.onQuery(DHTAnnouncePeerQuery.class, (sender, query) -> {
            routingTable.refreshNode(query.getNodeId(), sender);

            if (tokenManager.validateToken(query.getToken(), sender)) {
                SocketAddress address = sender;

                if (!query.isImpliedPort()) {
                    address = SocketAddress.inetSocketAddress(query.getPort(), sender.hostAddress());
                }

                routingTable.addPeerForTorrent(query.getInfoHash(), address);
            } else {
                throw DHTErrorException.create(203, "Bad token");
            }

            return DHTAnnouncePeerResponse.builder()
                    .nodeId(routingTable.getNodeId())
                    .build();
        });

        timerId = vertx.setPeriodic(60_000, id -> {
            refreshBuckets();
        });

        saveTableTimerId = vertx.setPeriodic(1_000, id -> {
            if (tableUpdated) {
                tableUpdated = false;

                log.info("Writing updated routing table to file");

                try (var outputStream = new FileOutputStream(dhtFile)) {
                    routingTable.writeTo(outputStream);
                } catch (IOException e) {
                    log.error("Error writing routing table to file:", e);
                }
            }
        });

        String bootstrapNode = System.getenv("DHT_BOOTSTRAP_NODE");

        if (StringUtils.isNotBlank(bootstrapNode)) {
            log.info("Using bootstrap node: {}", bootstrapNode);

            String[] parts = bootstrapNode.split(":");

            try {
                int port = Integer.parseInt(parts[1]);

                SocketAddress socketAddress = SocketAddress.inetSocketAddress(port, parts[0]);

                log.info("Resolved address: {}", socketAddress.hostAddress());

                protocolHandler
                        .query(
                                socketAddress,
                                DHTPingQuery.builder()
                                        .nodeId(routingTable.getNodeId())
                                        .build())
                        .onFailure(e -> log.error("Error while querying bootstrap node: ", e))
                        .onSuccess(res -> {
                            routingTable.refreshNode(res.getNodeId(), socketAddress);

                            recursiveFindNode();
                        });

            } catch (Exception e) {
                log.error("Could not parse DHT_BOOTSTRAP_NODE: ", e.getMessage());
            }
        }
    }

    public Future<Void> close() {
        vertx.cancelTimer(timerId);
        vertx.cancelTimer(saveTableTimerId);

        return Future.join(protocolHandler.close(), tokenManager.close()).mapEmpty();
    }

    private byte[] nodesToBytes(Collection<DHTNode> nodes) {
        ByteBuffer buffer = ByteBuffer.allocate(nodes.size() * 26).order(ByteOrder.BIG_ENDIAN);

        for (var n : nodes) {
            n.writeCompact(buffer);
        }

        return buffer.array();
    }

    private void refreshBuckets() {
        routingTable.findBucketToRefresh().ifPresent(this::findNode);
    }

    private void findNode(DHTBucket bucket) {
        var target = DHTNodeId.random(bucket.getMin(), bucket.getMax());

        DHTNode node = bucket.getRandomNode();

        log.debug("Asking for further nodes on id {} from {}", target.toHexString(), node);

        protocolHandler
                .query(
                        node.getAddress(),
                        DHTFindNodeQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .target(target)
                                .build())
                .onFailure(e -> {
                    log.error("Find Node Query failed: ", e);
                })
                .onSuccess(res -> {
                    ByteBuffer buffer = ByteBuffer.wrap(res.getNodes()).order(ByteOrder.BIG_ENDIAN);

                    log.debug("Received {} nodes from {}", res.getNodes().length / 26, node);

                    DHTNode n;
                    while ((n = DHTNode.fromCompact(buffer)) != null) {
                        routingTable.refreshNode(n.getNodeId(), n.getAddress());
                    }
                });
    }

    private void recursiveFindNode() {
        DHTBucket bucket = getOwnBucket();

        var target = DHTNodeId.random(bucket.getMin(), bucket.getMax());

        var nodes = routingTable.findClosestNodesForId(routingTable.getNodeId());

        // log.debug("Found closest nodes for id: {}", routingTable.getNodeId());
        // nodes.forEach(n -> log.debug("{}", n));

        DHTNode node = nodes.get(new SecureRandom().nextInt(nodes.size()));

        // DHTNode node = bucket.getRandomNode();

        log.debug("Asking for further nodes on id {} from {}", target.toHexString(), node);

        protocolHandler
                .query(
                        node.getAddress(),
                        DHTFindNodeQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .target(target)
                                .build())
                .onFailure(e -> {
                    log.error("Find Node Query failed: ", e);
                    vertx.setTimer(1_000, id -> recursiveFindNode());
                })
                .onSuccess(res -> {
                    ByteBuffer buffer = ByteBuffer.wrap(res.getNodes()).order(ByteOrder.BIG_ENDIAN);

                    log.debug("Received {} nodes from {}", res.getNodes().length / 26, node);

                    boolean newNodes = false;

                    DHTNode n;
                    while ((n = DHTNode.fromCompact(buffer)) != null) {
                        // log.debug("{}", n);

                        if (routingTable.findNodeById(n.getNodeId()).isEmpty()) {
                            newNodes = true;
                        }

                        routingTable.addNode(n.getNodeId(), n.getAddress());
                    }

                    // if (newNodes) {
                    log.debug("Queuing search for further nodes");
                    vertx.setTimer(1_000, id -> recursiveFindNode());
                    // }
                });
    }

    private DHTLookup lookupTorrent(byte[] infoHash) {
        return DHTLookup.forTorrent(vertx, protocolHandler, routingTable, infoHash);
    }

    public void torrent(byte[] infoHash, Handler<List<Peer>> peersHandler) {
        DHTNodeId key = new DHTNodeId(infoHash);

        for (var l : activeLookups) {
            if (l.getKey().equals(key)) {
                return;
            }
        }

        log.info("Starting lookup for key {}", key);

        var lookup = lookupTorrent(infoHash);

        activeLookups.add(lookup);

        lookup.start().onSuccess(nodes -> {
            activeLookups.remove(lookup);

            for (var n : nodes) {
                getPeers(n, infoHash).flatMap(res -> {
                    var peers = res.getValues().stream()
                            .flatMap(v -> TrackerResponse.parsePeers4(v).stream())
                            .toList();

                    peersHandler.handle(peers);

                    byte[] token = res.getToken();

                    return announcePeer(n, infoHash, token);
                });
            }
        });
    }

    private Future<DHTGetPeersResponse> getPeers(DHTNode node, byte[] infoHash) {
        return protocolHandler
                .query(
                        node,
                        DHTGetPeersQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .infoHash(infoHash)
                                .build())
                .onFailure(e -> log.error("Error on get peers: ", e));
    }

    private Future<DHTAnnouncePeerResponse> announcePeer(DHTNode node, byte[] infoHash, byte[] token) {
        return protocolHandler
                .query(
                        node,
                        DHTAnnouncePeerQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .infoHash(infoHash)
                                .token(token)
                                .port(6882)
                                .build())
                .onFailure(e -> log.error("Error on announce peer: ", e))
                .onSuccess(res -> log.info("Successfully announced peer to {}", node));
    }

    private DHTBucket getOwnBucket() {
        return routingTable.getBucketForId(routingTable.getNodeId());
    }
}
