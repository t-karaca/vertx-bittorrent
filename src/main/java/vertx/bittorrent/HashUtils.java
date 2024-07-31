package vertx.bittorrent;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class HashUtils {
    private HashUtils() {}

    public static MessageDigest getSha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha1(byte[] data) {
        MessageDigest digest = getSha1();
        return digest.digest(data);
    }

    public static byte[] sha1(ByteBuffer data) {
        MessageDigest digest = getSha1();
        digest.update(data);
        return digest.digest();
    }

    public static byte[] sha1(Buffer buffer) {
        MessageDigest digest = getSha1();
        return digest.digest(buffer.getBytes());
    }

    public static boolean isEqual(byte[] data, byte[] otherData) {
        return Arrays.equals(data, otherData);
    }

    public static boolean isEqual(ByteBuffer data, byte[] otherData) {
        return data.equals(ByteBuffer.wrap(otherData));
    }

    public static boolean isEqual(byte[] data, ByteBuffer otherData) {
        return isEqual(otherData, data);
    }
}
