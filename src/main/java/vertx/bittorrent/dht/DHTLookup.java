package vertx.bittorrent.dht;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.dht.messages.DHTFindNodeQuery;

@Slf4j
public class DHTLookup {

    private static final int MAX_NODES = 8;

    private final Vertx vertx;
    private final DHTProtocolHandler protocolHandler;
    private final DHTRoutingTable routingTable;

    @Getter
    private final DHTNodeId key;

    private final Random random = new SecureRandom();

    private long timerId;

    private int activeLookups = 0;

    private List<DHTLookupNode> closestNodes;

    private Promise<List<DHTNode>> promise;

    public DHTLookup(Vertx vertx, DHTProtocolHandler protocolHandler, DHTRoutingTable routingTable, DHTNodeId key) {
        this.vertx = vertx;
        this.protocolHandler = protocolHandler;
        this.routingTable = routingTable;
        this.key = key;
    }

    public Future<Void> cancel() {
        vertx.cancelTimer(timerId);

        return Future.succeededFuture();
    }

    private <T> T reservoirSample(T a, T b) {
        return random.nextBoolean() ? a : b;
    }

    public Future<List<DHTNode>> start() {
        if (promise == null) {
            promise = Promise.promise();

            lookup();
            lookup();
            lookup();
        }

        return promise.future();
    }

    private void lookup() {
        Optional<DHTLookupNode> optional = closestNodes.stream()
                .filter(n -> !n.isQueryFailed())
                .limit(MAX_NODES)
                .filter(n -> !n.isQueried() && !n.isQuerying())
                .reduce(this::reservoirSample);

        if (optional.isEmpty()) {
            if (activeLookups == 0) {
                var result = closestNodes.stream()
                        .filter(n -> !n.isQueryFailed())
                        .limit(MAX_NODES)
                        .map(DHTLookupNode::getNode)
                        .toList();

                log.info("[{}] Lookup finished, found nodes: {}", key, result);

                promise.complete(result);
            }

            return;
        }

        DHTLookupNode node = optional.get();

        node.setQuerying(true);

        log.debug("[{}] Querying on node {}", key, node.getNode());

        activeLookups++;

        protocolHandler
                .query(
                        node.getNode(),
                        DHTFindNodeQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .target(key)
                                .build())
                .onFailure(e -> {
                    log.error("[{}] Failed get peers query", key, e);

                    node.setQueryFailed(true);
                    node.setQueried(true);

                    activeLookups--;

                    timerId = vertx.setTimer(1_000, id -> lookup());
                })
                .onSuccess(res -> {
                    activeLookups--;
                    node.setQueried(true);

                    // res.getValues().stream()
                    //         .map(TrackerResponse::parsePeers4)
                    //         .flatMap(Collection::stream)
                    //         .forEach(p -> log.info("[{}] Found peer: {}", key, p));

                    ByteBuffer buffer = ByteBuffer.wrap(res.getNodes()).order(ByteOrder.BIG_ENDIAN);

                    log.debug("[{}] Received {} nodes from {}", key, res.getNodes().length / 26, node.getNode());

                    DHTNode n;
                    while ((n = DHTNode.fromCompact(buffer)) != null) {
                        log.debug("[{}] {}", key, n);

                        addNode(n);
                    }

                    timerId = vertx.setTimer(1_000, id -> lookup());
                });
    }

    private boolean addNode(DHTNode node) {
        if (closestNodes.stream().anyMatch(n -> n.getNodeId().equals(node.getNodeId()))) {
            return false;
        }

        if (closestNodes.size() < MAX_NODES) {
            closestNodes.add(new DHTLookupNode(node));
            closestNodes.sort(DHTLookupNode.distanceComparator(key));
            return true;
        }

        int lastIndex = Math.min(MAX_NODES - 1, closestNodes.size() - 1);
        DHTLookupNode farthestNode = closestNodes.get(lastIndex);
        if (node.getNodeId().distance(key).lessThan(farthestNode.getNodeId().distance(key))) {
            closestNodes.add(new DHTLookupNode(node));
            closestNodes.sort(DHTLookupNode.distanceComparator(key));
            return true;
        }

        return false;
    }

    public static DHTLookup forTorrent(
            Vertx vertx, DHTProtocolHandler protocolHandler, DHTRoutingTable routingTable, byte[] infoHash) {
        var key = new DHTNodeId(infoHash);

        var lookup = new DHTLookup(vertx, protocolHandler, routingTable, key);

        lookup.closestNodes = new ArrayList<>();

        var nodes = routingTable.findClosestNodesForId(key);

        for (var n : nodes) {
            lookup.closestNodes.add(new DHTLookupNode(n));
        }

        lookup.closestNodes.sort(DHTLookupNode.distanceComparator(key));

        return lookup;
    }
}
