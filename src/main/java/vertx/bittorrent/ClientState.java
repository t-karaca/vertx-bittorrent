package vertx.bittorrent;

import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientState {
    private Torrent torrent;
    private byte[] peerId = generatePeerId();

    private static byte[] generatePeerId() {
        return "01234567890123456789".getBytes(StandardCharsets.UTF_8);
    }
}
