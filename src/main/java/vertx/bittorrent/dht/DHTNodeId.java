package vertx.bittorrent.dht;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DHTNodeId implements Comparable<DHTNodeId> {
    public static final DHTNodeId MIN;
    public static final DHTNodeId MAX;

    public static final int NUM_BYTES = 20;

    private final byte[] bytes;

    static {
        MIN = new DHTNodeId(new byte[NUM_BYTES]);
        MAX = new DHTNodeId(new byte[NUM_BYTES + 1]);

        MAX.bytes[0] = 1;
    }

    public DHTNodeId half() {
        byte[] newBytes = Arrays.copyOf(bytes, bytes.length);

        boolean carryOver = false;
        for (int i = 0; i < newBytes.length; i++) {
            newBytes[i] = (byte) ((newBytes[i] & 0xFF) >> 1);

            if (carryOver) {
                newBytes[i] |= 0x80;
                carryOver = false;
            }

            if ((bytes[i] & 0x1) == 0x1) {
                carryOver = true;
            }
        }

        return new DHTNodeId(newBytes);
    }

    public DHTNodeId distance(DHTNodeId target) {
        return new DHTNodeId(UnsignedNumber.distance(bytes, target.bytes));
    }

    public DHTNodeId withBitAt(int index, boolean value) {
        if (this == MAX) {
            return MIN.withBitAt(index, value);
        }

        byte[] newBytes = Arrays.copyOf(bytes, bytes.length);

        int byteIndex = index / 8;

        if (byteIndex < 0 || byteIndex >= bytes.length) {
            throw new IndexOutOfBoundsException();
        }

        int bitIndex = index % 8;

        if (value) {
            newBytes[byteIndex] = (byte) ((newBytes[byteIndex] & 0xFF) | (0x80 >>> bitIndex));
        } else {
            newBytes[byteIndex] = (byte) ((newBytes[byteIndex] & 0xFF) & ~(0x80 >>> bitIndex));
        }

        return new DHTNodeId(newBytes);
    }

    @Override
    public String toString() {
        return toHexString();
    }

    public String toHexString() {
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public int compareTo(DHTNodeId o) {
        return UnsignedNumber.compare(bytes, o.bytes);
        // int numBytes = Math.max(bytes.length, o.bytes.length);
        //
        // int leftPadding = Math.max(o.bytes.length - bytes.length, 0);
        // int rightPadding = Math.max(bytes.length - o.bytes.length, 0);
        //
        // for (int i = 0; i < numBytes; i++) {
        //     byte left = i < leftPadding ? 0 : bytes[i - leftPadding];
        //     byte right = i < rightPadding ? 0 : o.bytes[i - rightPadding];
        //
        //     int res = Byte.compareUnsigned(left, right);
        //
        //     if (res != 0) {
        //         return res;
        //     }
        // }
        //
        // return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof DHTNodeId otherId) {
            return compareTo(otherId) == 0;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    public boolean lessThan(DHTNodeId o) {
        return compareTo(o) < 0;
    }

    public boolean lessOrEquals(DHTNodeId o) {
        return compareTo(o) <= 0;
    }

    public boolean greaterThan(DHTNodeId o) {
        return compareTo(o) > 0;
    }

    public boolean greaterOrEquals(DHTNodeId o) {
        return compareTo(o) >= 0;
    }

    public static DHTNodeId random() {
        byte[] bytes = new byte[NUM_BYTES];

        var random = new SecureRandom();

        random.nextBytes(bytes);

        return new DHTNodeId(bytes);
    }

    public static DHTNodeId random(DHTNodeId min, DHTNodeId max) {
        byte[] bytes = new byte[NUM_BYTES];

        var random = new SecureRandom();

        do {
            random.nextBytes(bytes);
        } while (UnsignedNumber.lessThan(bytes, min.bytes) || UnsignedNumber.greaterOrEquals(bytes, max.bytes));

        return new DHTNodeId(bytes);
    }

    public static DHTNodeId fromHex(String hex) {
        byte[] bytes = HexFormat.of().parseHex(hex);

        return new DHTNodeId(bytes);
    }
}
