package vertx.bittorrent.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public class DHTBucket {
    private static final long STATUS_THRESHOLD = 15 * 60 * 1000; // 15min in ms

    private static final int MAX_NODES = 8;

    private DHTNodeId min;
    private DHTNodeId max;
    private int splitLevel;
    private long lastUpdatedAt;

    private final List<DHTNode> nodes;

    @JsonIgnore
    private Handler<DHTBucket> refreshedHandler;

    public DHTBucket(DHTNodeId min, DHTNodeId max, int splitLevel) {
        this.min = min;
        this.max = max;
        this.splitLevel = splitLevel;
        this.lastUpdatedAt = -1;

        this.nodes = new ArrayList<>();
    }

    @JsonCreator(mode = Mode.PROPERTIES)
    public DHTBucket(
            @JsonProperty("min") DHTNodeId min,
            @JsonProperty("max") DHTNodeId max,
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
        // int index = -1;
        // int failedQueries = -1;
        //
        // for (int i = 0; i < nodes.size(); i++) {
        //     var node = nodes.get(i);
        //
        //     if (node.isBad()) {
        //         if (index == -1 || failedQueries < node.getNumFailedQueries()) {
        //             index = i;
        //             failedQueries = node.getNumFailedQueries();
        //         }
        //     }
        // }

        var badNodes = nodes.stream().filter(DHTNode::isBad).toList();

        badNodes.forEach(n -> n.onRefresh(null));

        nodes.removeAll(badNodes);

        // if (index != -1) {
        //     nodes.get(index).onRefresh(null);
        //
        //     nodes.remove(index);
        // }
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
        return nodes.size() == MAX_NODES;
    }

    @JsonIgnore
    public DHTNode getRandomNode() {
        var random = new SecureRandom();

        int index = random.nextInt(nodes.size());

        return nodes.get(index);
    }

    public Optional<DHTNode> findNodeById(DHTNodeId nodeId) {
        for (var node : nodes) {
            if (node.getNodeId().equals(nodeId)) {
                return Optional.of(node);
            }
        }

        return Optional.empty();
    }

    public DHTBucket split() {
        DHTNodeId mid = min.withBitAt(splitLevel, true);

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

    public boolean canContain(DHTNodeId nodeId) {
        return nodeId.greaterOrEquals(min) && nodeId.lessThan(max);
    }

    public static final DHTBucket initial() {
        return new DHTBucket(DHTNodeId.MIN, DHTNodeId.MAX, 0);
    }
}
