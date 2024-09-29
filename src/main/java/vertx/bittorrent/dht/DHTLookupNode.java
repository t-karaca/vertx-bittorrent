package vertx.bittorrent.dht;

import java.util.Comparator;
import lombok.Getter;
import lombok.Setter;

public class DHTLookupNode {
    @Getter
    private final DHTNode node;

    @Getter
    @Setter
    private boolean queryFailed;

    @Getter
    @Setter
    private boolean querying;

    @Getter
    @Setter
    private boolean queried;

    public DHTLookupNode(DHTNode node) {
        this.node = node;
    }

    public DHTNodeId getNodeId() {
        return node.getNodeId();
    }

    public static Comparator<DHTLookupNode> distanceComparator(DHTNodeId key) {
        return (a, b) -> a.getNode()
                .getNodeId()
                .distance(key)
                .compareTo(b.getNode().getNodeId().distance(key));
    }
}
