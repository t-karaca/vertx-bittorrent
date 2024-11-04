package vertx.bittorrent.dht;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import vertx.bittorrent.dht.exception.DHTErrorException;
import vertx.bittorrent.dht.messages.AnnouncePeerQuery;
import vertx.bittorrent.dht.messages.AnnouncePeerResponse;
import vertx.bittorrent.dht.messages.FindNodeQuery;
import vertx.bittorrent.dht.messages.FindNodeResponse;
import vertx.bittorrent.dht.messages.GetPeersQuery;
import vertx.bittorrent.dht.messages.GetPeersResponse;
import vertx.bittorrent.dht.messages.PingQuery;
import vertx.bittorrent.dht.messages.PingResponse;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.model.Peer;
import vertx.bittorrent.utils.AddressUtils;
import vertx.bittorrent.utils.RandomUtils;

@Slf4j
public class DHTClient {

    private static final int FIND_NODE_CONCURRENCY = 3;

    private final Vertx vertx;

    private final DHTProtocolHandler protocolHandler;
    private final DHTTokenManager tokenManager;
    private final DHTRoutingTable routingTable;

    private final long refreshBucketsTimerId;
    private final long saveTableTimerId;

    private long nextSearchTimerId = -1;

    private boolean tableUpdated = false;

    private List<DHTLookup> activeLookups = new ArrayList<>();

    private SocketAddress bootstrapAddress = null;

    private int activeFindNodeQueries = 0;
    private int emptyFindNodeQueries = 0;

    public DHTClient(Vertx vertx) {
        log.info("Starting dht client");

        this.vertx = vertx;
        this.protocolHandler = new DHTProtocolHandler(vertx);
        this.tokenManager = new DHTTokenManager(vertx);

        protocolHandler.onQuery(PingQuery.class, this::onPingQuery);
        protocolHandler.onQuery(FindNodeQuery.class, this::onFindNodeQuery);
        protocolHandler.onQuery(GetPeersQuery.class, this::onGetPeersQuery);
        protocolHandler.onQuery(AnnouncePeerQuery.class, this::onAnnouncePeerQuery);

        File dhtFile = new File("dht.json");
        if (dhtFile.exists()) {
            log.info("Using existing routing table from dht.json");

            try (var inputStream = new FileInputStream(dhtFile)) {
                routingTable = DHTRoutingTable.parse(inputStream);
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

        refreshBucketsTimerId = vertx.setPeriodic(60_000, id -> {
            routingTable.findBucketToRefresh().ifPresent(this::findNodeForBucket);
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

        if (routingTable.isEmpty()) {
            String bootstrapNode = System.getenv("DHT_BOOTSTRAP_NODE");

            if (StringUtils.isNotBlank(bootstrapNode)) {
                log.info("Using bootstrap node: {}", bootstrapNode);

                bootstrapAddress = AddressUtils.addressFromString(bootstrapNode);

                log.info("Resolved address: {}", bootstrapAddress.hostAddress());

                protocolHandler
                        .query(bootstrapAddress, new PingQuery(routingTable.getNodeId()))
                        .onFailure(e -> log.error("Error while querying bootstrap node: ", e))
                        .onSuccess(res -> {
                            routingTable.refreshNode(res.getNodeId(), bootstrapAddress);

                            searchNeighbors();
                        });
            } else {
                log.warn("Routing table is empty and no bootstrap node was specified");
            }
        } else {
            searchNeighbors();
        }
    }

    public Future<Void> close() {
        vertx.cancelTimer(refreshBucketsTimerId);
        vertx.cancelTimer(saveTableTimerId);
        vertx.cancelTimer(nextSearchTimerId);

        return Future.join(protocolHandler.close(), tokenManager.close()).mapEmpty();
    }

    private PingResponse onPingQuery(SocketAddress sender, PingQuery query) {
        routingTable.refreshNode(query.getNodeId(), sender);

        return new PingResponse(routingTable.getNodeId());
    }

    private FindNodeResponse onFindNodeQuery(SocketAddress sender, FindNodeQuery query) {
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
    }

    private GetPeersResponse onGetPeersQuery(SocketAddress sender, GetPeersQuery query) {
        routingTable.refreshNode(query.getNodeId(), sender);

        HashKey infoHash = HashKey.fromBytes(query.getInfoHash());

        List<byte[]> peers = routingTable.findPeersForTorrent(infoHash);

        byte[] nodes = DHTNode.allToCompact(routingTable.findClosestNodesForId(infoHash));

        byte[] token = tokenManager.createToken(sender);

        return GetPeersResponse.builder()
                .nodeId(routingTable.getNodeId())
                .values(peers)
                .nodes(nodes)
                .token(token)
                .build();
    }

    private AnnouncePeerResponse onAnnouncePeerQuery(SocketAddress sender, AnnouncePeerQuery query) {
        routingTable.refreshNode(query.getNodeId(), sender);

        if (tokenManager.validateToken(query.getToken(), sender)) {
            SocketAddress address = sender;

            if (!query.isImpliedPort()) {
                address = SocketAddress.inetSocketAddress(query.getPort(), sender.hostAddress());
            }

            routingTable.addPeerForTorrent(HashKey.fromBytes(query.getInfoHash()), address);
        } else {
            throw DHTErrorException.create(203, "Bad token");
        }

        return AnnouncePeerResponse.builder().nodeId(routingTable.getNodeId()).build();
    }

    private void findNodeForBucket(DHTBucket bucket) {
        var target = HashKey.random(bucket.getMin(), bucket.getMax());

        bucket.getRandomNode().ifPresent(node -> {
            log.debug("Refresh bucket: find nodes on id {} from {}", target, node);

            findNode(node, target).onSuccess(res -> DHTNode.allFromCompact(res.getNodes())
                    .forEach(n -> routingTable.refreshNode(n.getNodeId(), n.getAddress())));
        });
    }

    private void searchNeighbors() {
        int numQueries = FIND_NODE_CONCURRENCY - activeFindNodeQueries;
        if (numQueries <= 0) {
            return;
        }

        DHTBucket bucket = routingTable.getBucketForId(routingTable.getNodeId());

        var target = HashKey.random(bucket.getMin(), bucket.getMax());

        var closestNodes = routingTable.findClosestNodesForId(routingTable.getNodeId());

        for (int i = 0; i < numQueries; i++) {
            DHTNode queriedNode = closestNodes.stream()
                    .filter(n -> !n.isQuerying())
                    .reduce(RandomUtils::reservoirSample)
                    .orElse(null);

            if (queriedNode == null) {
                return;
            }

            log.debug("Asking for further nodes on id {} from {}", target, queriedNode);

            activeFindNodeQueries++;
            queriedNode.setQuerying(true);

            findNode(queriedNode, target).onComplete(ar -> {
                activeFindNodeQueries--;
                queriedNode.setQuerying(false);

                if (ar.succeeded()) {
                    var res = ar.result();
                    var nodes = DHTNode.allFromCompact(res.getNodes());

                    int newNodes = 0;

                    for (var n : nodes) {
                        if (routingTable.findNodeById(n.getNodeId()).isEmpty()
                                && routingTable
                                        .addNode(n.getNodeId(), n.getAddress())
                                        .isPresent()) {
                            newNodes++;
                        }
                    }

                    log.debug("Found {} new nodes", newNodes);

                    if (newNodes > 0) {
                        emptyFindNodeQueries = 0;
                        queueSearchNeighbors(1_000);
                    } else {
                        emptyFindNodeQueries++;

                        if (emptyFindNodeQueries >= 3) {
                            queueSearchNeighbors(60_000);
                        } else {
                            queueSearchNeighbors(1_000);
                        }
                    }
                } else {
                    emptyFindNodeQueries = 0;
                    queueSearchNeighbors(1_000);
                }
            });
        }
    }

    private void queueSearchNeighbors(long delay) {
        if (nextSearchTimerId > -1) {
            vertx.cancelTimer(nextSearchTimerId);
        }

        log.debug("Queuing search for neighbors after {}ms", delay);
        nextSearchTimerId = vertx.setTimer(delay, id -> searchNeighbors());
    }

    public void lookupTorrent(byte[] infoHash, Handler<List<Peer>> peersHandler) {
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
                .onFailure(e -> log.debug("Error on find node: {}", e.getMessage()));
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
                .onFailure(e -> log.debug("Error on announce peer: {}", e.getMessage()))
                .onSuccess(res -> log.info("Successfully announced to {}", node));
    }
}
