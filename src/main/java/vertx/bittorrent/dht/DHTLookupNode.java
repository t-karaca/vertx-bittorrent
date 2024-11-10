package vertx.bittorrent.dht;

import java.util.Comparator;
import lombok.Getter;
import lombok.Setter;
import vertx.bittorrent.model.HashKey;

public class DHTLookupNode {
    @Getter
    private final DHTNode node;

    @Getter
    @Setter
    private byte[] token;

    @Getter
    @Setter
    private long sequenceNumber = -1;

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

    @Override
    public String toString() {
        return node.toString();
    }

    public HashKey getNodeId() {
        return node.getNodeId();
    }

    public static Comparator<DHTLookupNode> distanceComparator(HashKey key) {
        return (a, b) -> a.getNode()
                .getNodeId()
                .distance(key)
                .compareTo(b.getNode().getNodeId().distance(key));
    }
}
