package vertx.bittorrent;

import java.util.Arrays;

public class HashKey {
    private final byte[] bytes;

    public HashKey(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof HashKey key) {
            return Arrays.equals(bytes, key.bytes);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
