package vertx.bittorrent;

import io.vertx.core.net.SocketAddress;
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

    public static Peer fromDict(BEncodedDict dict) {
        return new Peer(
                SocketAddress.inetSocketAddress(dict.requireInt("port"), dict.requireString("ip")),
                dict.findString("peer id").orElse(null));
    }
}
