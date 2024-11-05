package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class ClientState {
    private final Vertx vertx;

    @Getter
    private final byte[] peerId = generatePeerId();

    @Getter
    private long totalBytesDownloaded = 0L;

    @Getter
    private long totalBytesUploaded = 0L;

    @Getter
    @Setter
    private int serverPort;

    public ClientState(Vertx vertx) {
        this.vertx = vertx;
    }

    public void addTotalBytesDownloaded(long bytes) {
        totalBytesDownloaded += bytes;
    }

    public void addTotalBytesUploaded(long bytes) {
        totalBytesUploaded += bytes;
    }

    public Future<Void> close() {
        return Future.succeededFuture();
    }

    private static byte[] generatePeerId() {
        byte[] prefixBytes = "-VB1000-".getBytes(StandardCharsets.UTF_8);

        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);

        return ArrayUtils.addAll(prefixBytes, bytes);
    }
}
