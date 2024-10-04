package vertx.bittorrent.dht;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

public class HashKey implements Comparable<HashKey> {
    public static final int NUM_BYTES = 20;
    public static final int NUM_BITS = NUM_BYTES * 8;
    public static final int HEX_LENGTH = NUM_BYTES * 2;

    public static final HashKey MIN = new HashKey(BigInteger.ZERO);
    public static final HashKey MAX = new HashKey(BigInteger.ZERO.setBit(NUM_BITS));

    private static final Random RANDOM = new SecureRandom();

    private final BigInteger bigInt;

    public HashKey(BigInteger bigInteger) {
        this.bigInt = bigInteger;
    }

    public HashKey(byte[] bytes) {
        this.bigInt = new BigInteger(1, bytes);
    }

    @Override
    public String toString() {
        String hex = bigInt.toString(16);

        int padding = HEX_LENGTH - hex.length();
        if (padding > 0) {
            // hex string is shorter than 40 characters
            StringBuilder builder = new StringBuilder(HEX_LENGTH);

            for (int i = 0; i < padding; i++) {
                builder.append('0');
            }

            builder.append(hex);

            hex = builder.toString();
        }

        return hex;
    }

    @Override
    public int compareTo(HashKey o) {
        return bigInt.compareTo(o.bigInt);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof HashKey otherId) {
            return compareTo(otherId) == 0;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return bigInt.hashCode();
    }

    public byte[] getBytes() {
        byte[] bytes = bigInt.toByteArray();

        int padding = NUM_BYTES - bytes.length;
        if (padding > 0) {
            // we have less than 20 bytes, padding with zeroes required
            byte[] b = new byte[NUM_BYTES];

            System.arraycopy(bytes, 0, b, padding, bytes.length);

            return b;
        } else if (padding < 0) {
            // we have more than 20 bytes, because of an extra byte for the sign bit from BigInteger
            return Arrays.copyOfRange(bytes, -padding, bytes.length);
        }

        return bytes;
    }

    public HashKey distance(HashKey target) {
        return new HashKey(bigInt.xor(target.bigInt));
    }

    public HashKey withBitAt(int index) {
        if (index >= NUM_BITS) {
            throw new IndexOutOfBoundsException();
        }

        int i = NUM_BITS - index - 1;

        return new HashKey(bigInt.setBit(i));
    }

    public boolean lessThan(HashKey o) {
        return compareTo(o) < 0;
    }

    public boolean lessOrEquals(HashKey o) {
        return compareTo(o) <= 0;
    }

    public boolean greaterThan(HashKey o) {
        return compareTo(o) > 0;
    }

    public boolean greaterOrEquals(HashKey o) {
        return compareTo(o) >= 0;
    }

    public static HashKey random() {
        return new HashKey(new BigInteger(NUM_BITS, RANDOM));
    }

    public static HashKey random(HashKey min, HashKey max) {
        BigInteger range = max.bigInt.subtract(min.bigInt);

        BigInteger value;

        do {
            value = new BigInteger(range.bitLength(), RANDOM);
        } while (value.compareTo(range) >= 0);

        return new HashKey(min.bigInt.add(value));
    }

    public static HashKey fromHex(String hex) {
        return new HashKey(new BigInteger(hex, 16));
    }

    public static HashKey fromString(String id) {
        return fromBytes(id.getBytes(StandardCharsets.UTF_8));
    }

    public static HashKey fromBytes(byte[] bytes) {
        return new HashKey(bytes);
    }
}
