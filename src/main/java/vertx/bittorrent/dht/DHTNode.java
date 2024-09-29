package vertx.bittorrent.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import io.vertx.core.net.SocketAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import lombok.Getter;

@Getter
public class DHTNode {
    private static final long STATUS_THRESHOLD = 15 * 60 * 1000; // 15min in ms
    private static final long FAILURE_THRESHOLD = 3;

    private final DHTNodeId nodeId;

    private final SocketAddress address;

    private int numFailedQueries;
    private long lastUpdatedAt;

    @JsonIgnore
    private Handler<DHTNode> refreshedHandler;

    public DHTNode(DHTNodeId nodeId, SocketAddress address) {
        this.nodeId = nodeId;
        this.address = address;

        numFailedQueries = 0;
        lastUpdatedAt = -1;
    }

    @JsonCreator(mode = Mode.PROPERTIES)
    public DHTNode(
            @JsonProperty("nodeId") DHTNodeId nodeId,
            @JsonProperty("address") SocketAddress address,
            @JsonProperty("numFailedQueries") int numFailedQueries,
            @JsonProperty("lastUpdatedAt") long lastUpdatedAt) {
        this.nodeId = nodeId;
        this.address = address;
        this.numFailedQueries = numFailedQueries;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public DHTNode onRefresh(Handler<DHTNode> handler) {
        refreshedHandler = handler;
        return this;
    }

    @Override
    public String toString() {
        return "DHTNode(nodeId=" + nodeId.toString() + ", address=" + address.toString() + ")";
    }

    public void addFailedQuery() {
        numFailedQueries++;
    }

    public void clearFailedQueries() {
        numFailedQueries = 0;
    }

    public void refresh() {
        lastUpdatedAt = System.currentTimeMillis();

        if (refreshedHandler != null) {
            refreshedHandler.handle(this);
        }
    }

    @JsonIgnore
    public long millisSinceLastUpdated() {
        return System.currentTimeMillis() - lastUpdatedAt;
    }

    @JsonIgnore
    public boolean isGood() {
        return lastUpdatedAt != -1 && millisSinceLastUpdated() < STATUS_THRESHOLD;
    }

    @JsonIgnore
    public boolean isBad() {
        return !isGood() && numFailedQueries >= FAILURE_THRESHOLD;
    }

    public boolean isSameAddress(SocketAddress address) {
        return this.address.equals(address);
    }

    public void writeCompact(ByteBuffer buffer) {
        if (buffer.order() != ByteOrder.BIG_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer has to be in big-endian");
        }

        if (buffer.remaining() < 26) {
            throw new BufferOverflowException();
        }

        buffer.put(nodeId.getBytes());

        try {
            InetAddress addr = InetAddress.getByName(address.hostAddress());
            buffer.put(addr.getAddress());

            buffer.putShort((short) address.port());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static DHTNode fromCompact(ByteBuffer buffer) {
        if (buffer.order() != ByteOrder.BIG_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer has to be in big-endian");
        }

        if (buffer.remaining() < 26) {
            return null;
        }

        byte[] nodeId = new byte[DHTNodeId.NUM_BYTES];
        byte[] addressBytes = new byte[4];

        buffer.get(nodeId);
        buffer.get(addressBytes);

        int port = ((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF);

        try {
            InetAddress addr = InetAddress.getByAddress(addressBytes);
            SocketAddress socketAddress = SocketAddress.inetSocketAddress(new InetSocketAddress(addr, port));

            return new DHTNode(new DHTNodeId(nodeId), socketAddress);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static Comparator<DHTNode> distanceComparator(DHTNodeId key) {
        return (a, b) -> a.getNodeId().distance(key).compareTo(b.getNodeId().distance(key));
    }
}
