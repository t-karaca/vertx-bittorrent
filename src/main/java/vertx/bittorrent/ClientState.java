package vertx.bittorrent;

import java.nio.charset.StandardCharsets;
import lombok.Getter;

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
        return "01234567890123456789".getBytes(StandardCharsets.UTF_8);
    }
}
