package vertx.bittorrent.dht;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import vertx.bittorrent.Peer;
import vertx.bittorrent.dht.exception.DHTErrorException;
import vertx.bittorrent.dht.messages.AnnouncePeerQuery;
import vertx.bittorrent.dht.messages.AnnouncePeerResponse;
import vertx.bittorrent.dht.messages.FindNodeQuery;
import vertx.bittorrent.dht.messages.FindNodeResponse;
import vertx.bittorrent.dht.messages.GetPeersQuery;
import vertx.bittorrent.dht.messages.GetPeersResponse;
import vertx.bittorrent.dht.messages.PingQuery;
import vertx.bittorrent.dht.messages.PingResponse;

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

        routingTable.onUpdated(v -> tableUpdated = true);

        log.info("Node id: {}", routingTable.getNodeId());

        protocolHandler.onQuery(PingQuery.class, (sender, query) -> {
            routingTable.refreshNode(query.getNodeId(), sender);

            return PingResponse.builder().nodeId(routingTable.getNodeId()).build();
        });

        protocolHandler.onQuery(FindNodeQuery.class, (sender, query) -> {
            routingTable.refreshNode(query.getNodeId(), sender);

            List<DHTNode> nodes = routingTable
                    .findNodeById(query.getTarget())
                    .map(Collections::singletonList)
                    .orElseGet(() -> routingTable.findClosestNodesForId(query.getTarget()));

            byte[] bytes = DHTNode.allToCompact(nodes);

            return FindNodeResponse.builder()
                    .nodeId(routingTable.getNodeId())
                    .nodes(bytes)
                    .build();
        });

        protocolHandler.onQuery(GetPeersQuery.class, (sender, query) -> {
            routingTable.refreshNode(query.getNodeId(), sender);

            byte[] infoHash = query.getInfoHash();

            var peers = routingTable.findPeersForTorrent(infoHash);

            byte[] nodes = DHTNode.allToCompact(routingTable.findClosestNodesForId(query.getNodeId()));

            byte[] token = tokenManager.createToken(sender);

            return GetPeersResponse.builder()
                    .nodeId(routingTable.getNodeId())
                    .values(peers)
                    .nodes(nodes)
                    .token(token)
                    .build();
        });

        protocolHandler.onQuery(AnnouncePeerQuery.class, (sender, query) -> {
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

            return AnnouncePeerResponse.builder()
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
                                PingQuery.builder()
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

    private void refreshBuckets() {
        routingTable.findBucketToRefresh().ifPresent(this::findNode);
    }

    private void findNode(DHTBucket bucket) {
        var target = HashKey.random(bucket.getMin(), bucket.getMax());

        bucket.getRandomNode().ifPresent(node -> {
            log.debug("Asking for further nodes on id {} from {}", target, node);

            protocolHandler
                    .query(
                            node.getAddress(),
                            FindNodeQuery.builder()
                                    .nodeId(routingTable.getNodeId())
                                    .target(target)
                                    .build())
                    .onFailure(e -> log.error("Find Node Query failed: {}", e.getMessage()))
                    .onSuccess(res -> DHTNode.allFromCompact(res.getNodes())
                            .forEach(n -> routingTable.refreshNode(n.getNodeId(), n.getAddress())));
        });
    }

    private void recursiveFindNode() {
        DHTBucket bucket = routingTable.getBucketForId(routingTable.getNodeId());

        var target = HashKey.random(bucket.getMin(), bucket.getMax());

        var nodes = routingTable.findClosestNodesForId(routingTable.getNodeId());

        DHTNode node = nodes.get(new SecureRandom().nextInt(nodes.size()));

        log.debug("Asking for further nodes on id {} from {}", target, node);

        protocolHandler
                .query(
                        node.getAddress(),
                        FindNodeQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .target(target)
                                .build())
                .onFailure(e -> {
                    log.error("Find Node Query failed: {}", e.getMessage());
                    vertx.setTimer(1_000, id -> recursiveFindNode());
                })
                .onSuccess(res -> {
                    DHTNode.allFromCompact(res.getNodes())
                            .forEach(n -> routingTable.addNode(n.getNodeId(), n.getAddress()));

                    // ByteBuffer buffer = ByteBuffer.wrap(res.getNodes()).order(ByteOrder.BIG_ENDIAN);
                    //
                    // log.debug("Received {} nodes from {}", res.getNodes().length / 26, node);
                    //
                    // boolean newNodes = false;
                    //
                    // DHTNode n;
                    // while ((n = DHTNode.fromCompact(buffer)) != null) {
                    //     // log.debug("{}", n);
                    //
                    //     if (routingTable.findNodeById(n.getNodeId()).isEmpty()) {
                    //         newNodes = true;
                    //     }
                    //
                    //     routingTable.addNode(n.getNodeId(), n.getAddress());
                    // }

                    // if (newNodes) {
                    log.debug("Queuing search for further nodes");
                    vertx.setTimer(1_000, id -> recursiveFindNode());
                    // }
                });
    }

    public void torrent(byte[] infoHash, Handler<List<Peer>> peersHandler) {
        HashKey key = new HashKey(infoHash);

        for (var l : activeLookups) {
            if (l.getKey().equals(key)) {
                return;
            }
        }

        log.info("Starting lookup for key {}", key);

        var lookup = DHTLookup.forTorrent(vertx, protocolHandler, routingTable, infoHash);

        lookup.onPeers(peersHandler);

        activeLookups.add(lookup);

        lookup.start().onSuccess(nodes -> {
            activeLookups.remove(lookup);

            for (var n : nodes) {
                if (n.getToken() != null) {
                    announcePeer(n.getNode(), infoHash, n.getToken());
                }
                // getPeers(n, infoHash).flatMap(res -> {
                //     var peers = res.getValues().stream()
                //             .flatMap(v -> TrackerResponse.parsePeers4(v).stream())
                //             .toList();
                //
                //     peersHandler.handle(peers);
                //
                //     byte[] token = res.getToken();
                //
                //     return announcePeer(n, infoHash, token);
                // });
            }
        });
    }

    private Future<FindNodeResponse> findNode(DHTNode node, HashKey target) {
        return protocolHandler
                .query(
                        node,
                        FindNodeQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .target(target)
                                .build())
                .onFailure(e -> log.error("Error on find node: {}", e.getMessage()));
    }

    private Future<GetPeersResponse> getPeers(DHTNode node, byte[] infoHash) {
        return protocolHandler
                .query(
                        node,
                        GetPeersQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .infoHash(infoHash)
                                .build())
                .onFailure(e -> log.error("Error on get peers: {}", e.getMessage()));
    }

    private Future<AnnouncePeerResponse> announcePeer(DHTNode node, byte[] infoHash, byte[] token) {
        return protocolHandler
                .query(
                        node,
                        AnnouncePeerQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .infoHash(infoHash)
                                .token(token)
                                .port(6882)
                                .build())
                .onFailure(e -> log.error("Error on announce peer: {}", e.getMessage()))
                .onSuccess(res -> log.info("Successfully announced peer to {}", node));
    }
}
