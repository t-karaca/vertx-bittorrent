package vertx.bittorrent;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import java.security.DigestException;
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

    public static void sha1(ByteBuffer data, byte[] output) {
        MessageDigest digest = getSha1();
        digest.update(data);

        try {
            digest.digest(output, 0, output.length);
        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha1(Buffer buffer) {
        MessageDigest digest = getSha1();
        return digest.digest(buffer.getBytes());
    }

    public static void sha1(byte[] data, int dataOffset, int dataLength, byte[] output) {
        MessageDigest digest = getSha1();
        digest.update(data, dataOffset, dataLength);

        try {
            digest.digest(output, 0, output.length);
        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
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
