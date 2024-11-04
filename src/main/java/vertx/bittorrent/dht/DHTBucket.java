package vertx.bittorrent.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.utils.RandomUtils;

@Slf4j
@Getter
public class DHTBucket {
    public static final int MAX_NODES = 8;

    private static final long STATUS_THRESHOLD = 15 * 60 * 1000; // 15min in ms

    private HashKey min;
    private HashKey max;
    private int splitLevel;
    private long lastUpdatedAt;

    private final List<DHTNode> nodes;

    @JsonIgnore
    private Handler<DHTBucket> refreshedHandler;

    public DHTBucket(HashKey min, HashKey max, int splitLevel) {
        this.min = min;
        this.max = max;
        this.splitLevel = splitLevel;
        this.lastUpdatedAt = -1;

        this.nodes = new ArrayList<>();
    }

    @JsonCreator(mode = Mode.PROPERTIES)
    public DHTBucket(
            @JsonProperty("min") HashKey min,
            @JsonProperty("max") HashKey max,
            @JsonProperty("splitLevel") int splitLevel,
            @JsonProperty("lastUpdatedAt") long lastUpdatedAt,
            @JsonProperty("nodes") Collection<DHTNode> nodes) {
        this.min = min;
        this.max = max;
        this.splitLevel = splitLevel;
        this.lastUpdatedAt = lastUpdatedAt;
        this.nodes = new ArrayList<>(nodes);
    }

    private void onNodeRefreshed(DHTNode node) {
        refresh();
    }

    public DHTBucket onRefresh(Handler<DHTBucket> handler) {
        refreshedHandler = handler;
        return this;
    }

    public void refresh() {
        lastUpdatedAt = System.currentTimeMillis();

        if (refreshedHandler != null) {
            refreshedHandler.handle(this);
        }
    }

    public boolean needsRefresh() {
        return System.currentTimeMillis() - lastUpdatedAt > STATUS_THRESHOLD;
    }

    public boolean addNode(DHTNode node) {
        if (nodes.size() < MAX_NODES && canContain(node)) {
            nodes.add(node);

            node.onRefresh(this::onNodeRefreshed);

            refresh();
            return true;
        }

        return false;
    }

    public void purgeBadNodes() {
        var badNodes = nodes.stream().filter(DHTNode::isBad).toList();

        if (!badNodes.isEmpty()) {
            badNodes.forEach(n -> n.onRefresh(null));

            log.debug("Purging {} bad nodes: {}", badNodes.size(), badNodes);

            nodes.removeAll(badNodes);
        }
    }

    public int nodesCount() {
        return nodes.size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    @JsonIgnore
    public boolean isFull() {
        return nodes.stream().filter(n -> !n.isBad()).count() == MAX_NODES;
    }

    @JsonIgnore
    public Optional<DHTNode> getRandomNode() {
        var list = nodes.stream().filter(n -> !n.isBad()).toList();

        if (!list.isEmpty()) {
            return Optional.of(RandomUtils.randomFrom(nodes));
        }

        return Optional.empty();
    }

    public Optional<DHTNode> findNodeById(HashKey nodeId) {
        for (var node : nodes) {
            if (node.getNodeId().equals(nodeId)) {
                return Optional.of(node);
            }
        }

        return Optional.empty();
    }

    public DHTBucket split() {
        HashKey mid = min.withBitAt(splitLevel);

        splitLevel++;

        DHTBucket bucket = new DHTBucket(min, mid, splitLevel);

        min = mid;

        for (var node : nodes) {
            if (bucket.canContain(node)) {
                bucket.nodes.add(node);

                node.onRefresh(bucket::onNodeRefreshed);
            }
        }

        nodes.removeAll(bucket.nodes);

        bucket.refresh();
        refresh();

        return bucket;
    }

    public boolean canContain(DHTNode node) {
        return canContain(node.getNodeId());
    }

    public boolean canContain(HashKey nodeId) {
        return nodeId.greaterOrEquals(min) && nodeId.lessThan(max);
    }

    public static final DHTBucket initial() {
        return new DHTBucket(HashKey.MIN, HashKey.MAX, 0);
    }
}
