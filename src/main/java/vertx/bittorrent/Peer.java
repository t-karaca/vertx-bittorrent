package vertx.bittorrent;

import io.vertx.core.net.SocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class Peer {
    private final SocketAddress address;
    private final String peerId;

    public Peer(SocketAddress address) {
        this(address, null);
    }

    public Peer(SocketAddress address, String peerId) {
        this.address = address;
        this.peerId = peerId;
    }

    @Override
    public String toString() {
        return address.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Peer otherPeer) {
            return address.equals(otherPeer.address);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    public byte[] toCompact() {
        return toCompact(address);
    }

    public static Peer fromDict(BEncodedDict dict) {
        return new Peer(
                SocketAddress.inetSocketAddress(dict.requireInt("port"), dict.requireString("ip")),
                dict.findString("peer id").orElse(null));
    }

    public static byte[] toCompact(SocketAddress address) {
        try {
            InetAddress ip = InetAddress.getByName(address.hostAddress());
            byte[] bytes = ip.getAddress();

            ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 2).order(ByteOrder.BIG_ENDIAN);

            buffer.put(bytes);

            buffer.put((byte) ((address.port() & 0xFF00) >> 2));
            buffer.put((byte) (address.port() & 0xFF));

            return buffer.array();
        } catch (UnknownHostException e) {
            // should not happen since we are using the resolved hostAddress
            throw new RuntimeException(e);
        }
    }
}
