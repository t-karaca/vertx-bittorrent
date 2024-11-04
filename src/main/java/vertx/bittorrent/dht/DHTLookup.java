package vertx.bittorrent.dht;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.dht.messages.GetPeersQuery;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.model.Peer;
import vertx.bittorrent.model.TrackerResponse;
import vertx.bittorrent.utils.RandomUtils;

@Slf4j
public class DHTLookup {

    private static final int MAX_NODES = 8;

    private final Vertx vertx;
    private final DHTProtocolHandler protocolHandler;
    private final DHTRoutingTable routingTable;

    @Getter
    private final HashKey key;

    private long timerId;

    private int activeLookups = 0;

    private List<DHTLookupNode> closestNodes;

    private Promise<List<DHTLookupNode>> promise;

    private Handler<List<Peer>> peersHandler;

    public DHTLookup(Vertx vertx, DHTProtocolHandler protocolHandler, DHTRoutingTable routingTable, HashKey key) {
        this.vertx = vertx;
        this.protocolHandler = protocolHandler;
        this.routingTable = routingTable;
        this.key = key;
    }

    public DHTLookup onPeers(Handler<List<Peer>> handler) {
        peersHandler = handler;
        return this;
    }

    public Future<Void> cancel() {
        vertx.cancelTimer(timerId);

        return Future.succeededFuture();
    }

    public Future<List<DHTLookupNode>> start() {
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
                .reduce(RandomUtils::reservoirSample);

        if (optional.isEmpty()) {
            if (activeLookups == 0) {
                var result = closestNodes.stream()
                        .filter(n -> !n.isQueryFailed())
                        .limit(MAX_NODES)
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
                        GetPeersQuery.builder()
                                .nodeId(routingTable.getNodeId())
                                .infoHash(key.getBytes())
                                .build())
                .onFailure(e -> {
                    log.error("[{}] Failed get peers query: {}", key, e.getMessage());

                    node.setQueryFailed(true);
                    node.setQueried(true);

                    activeLookups--;

                    timerId = vertx.setTimer(1_000, id -> lookup());
                })
                .onSuccess(res -> {
                    activeLookups--;
                    node.setQueried(true);

                    node.setToken(res.getToken());

                    var peers = res.getValues().stream()
                            .map(TrackerResponse::parsePeers4)
                            .flatMap(Collection::stream)
                            .toList();

                    if (!peers.isEmpty() && peersHandler != null) {
                        log.debug("[{}] Found {} peers", key, peers.size());
                        peersHandler.handle(peers);
                    }

                    DHTNode.allFromCompact(res.getNodes()).stream()
                            .peek(n -> log.debug("[{}] {}", key, n))
                            .forEach(this::addNode);

                    timerId = vertx.setTimer(1_000, id -> lookup());
                });
    }

    private void addNode(DHTNode node) {
        if (closestNodes.stream().anyMatch(n -> n.getNodeId().equals(node.getNodeId()))) {
            return;
        }

        closestNodes.add(new DHTLookupNode(node));
        closestNodes.sort(DHTLookupNode.distanceComparator(key));
    }

    public static DHTLookup forTorrent(
            Vertx vertx, DHTProtocolHandler protocolHandler, DHTRoutingTable routingTable, byte[] infoHash) {
        var key = new HashKey(infoHash);

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
