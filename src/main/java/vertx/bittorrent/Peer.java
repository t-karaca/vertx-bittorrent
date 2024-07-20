package vertx.bittorrent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Builder
@Slf4j
public class Peer {
    private final InetAddress address;
    private final int port;
    private final String peerId;

    @Override
    public String toString() {
        return address.getHostAddress() + ":" + port;
    }

    public static Peer fromDict(BEncodedDict dict) {
        try {
            return new Peer(
                    InetAddress.getByName(dict.requireString("ip")),
                    dict.requireInt("port"),
                    dict.findString("peer id").orElse(null));
        } catch (UnknownHostException e) {
            log.error("Could not resolve host for peer", e);
        }

        return null;
    }
}
