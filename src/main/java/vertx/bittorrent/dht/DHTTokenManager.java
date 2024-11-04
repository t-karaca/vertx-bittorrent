package vertx.bittorrent.dht;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;
import vertx.bittorrent.utils.HashUtils;

public class DHTTokenManager {
    private static final int SECRET_LENGTH = 8;

    private final Vertx vertx;

    private final byte[] secret = new byte[SECRET_LENGTH];
    private final byte[] oldSecret = new byte[SECRET_LENGTH];

    private final Random random = new SecureRandom();

    private long timerId;

    public DHTTokenManager(Vertx vertx) {
        this.vertx = vertx;

        random.nextBytes(secret);
        random.nextBytes(oldSecret);

        timerId = vertx.setPeriodic(300_000, id -> {
            rotateSecrets();
        });
    }

    public Future<Void> close() {
        vertx.cancelTimer(timerId);

        return Future.succeededFuture();
    }

    public byte[] createToken(SocketAddress address) {
        byte[] bytes = address.hostAddress().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length + SECRET_LENGTH);

        buffer.put(bytes);
        buffer.put(secret);

        return HashUtils.sha1(buffer);
    }

    public boolean validateToken(byte[] token, SocketAddress address) {
        byte[] bytes = address.hostAddress().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length + SECRET_LENGTH);

        buffer.put(bytes);
        buffer.mark();
        buffer.put(secret);

        if (HashUtils.hashEquals(buffer, token)) {
            return true;
        }

        buffer.reset();

        buffer.put(oldSecret);

        return HashUtils.hashEquals(buffer, token);
    }

    private void rotateSecrets() {
        System.arraycopy(secret, 0, oldSecret, 0, SECRET_LENGTH);

        random.nextBytes(secret);
    }
}
