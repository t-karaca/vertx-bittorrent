package vertx.bittorrent;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;

@Getter
public class ClientState {
    private Torrent torrent;
    private Bitfield bitfield;

    private final byte[] peerId = generatePeerId();

    public ClientState setTorrent(Torrent torrent) {
        this.torrent = torrent;
        this.bitfield = Bitfield.fromSize((int) torrent.getPiecesCount());
        return this;
    }

    private static byte[] generatePeerId() {
        byte[] prefixBytes = "-VB1000-".getBytes(StandardCharsets.UTF_8);

        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);

        return ArrayUtils.addAll(prefixBytes, bytes);
    }
}
