package vertx.bittorrent.dht;

public class UnsignedNumber {
    public static int compare(byte[] a, byte[] b) {
        int numBytes = Math.max(a.length, b.length);

        int leftPadding = Math.max(b.length - a.length, 0);
        int rightPadding = Math.max(a.length - b.length, 0);

        for (int i = 0; i < numBytes; i++) {
            byte left = i < leftPadding ? 0 : a[i - leftPadding];
            byte right = i < rightPadding ? 0 : b[i - rightPadding];

            int res = Byte.compareUnsigned(left, right);

            if (res != 0) {
                return res;
            }
        }

        return 0;
    }

    public static byte[] distance(byte[] a, byte[] b) {
        int numBytes = Math.max(a.length, b.length);

        byte[] bytes = new byte[numBytes];

        int leftPadding = Math.max(b.length - a.length, 0);
        int rightPadding = Math.max(a.length - b.length, 0);

        for (int i = 0; i < numBytes; i++) {
            byte left = i < leftPadding ? 0 : a[i - leftPadding];
            byte right = i < rightPadding ? 0 : b[i - rightPadding];

            bytes[i] = (byte) ((left & 0xFF) ^ (right & 0xFF));
        }

        return bytes;
    }

    public static boolean equals(byte[] a, byte[] b) {
        return compare(a, b) == 0;
    }

    public static boolean lessThan(byte[] a, byte[] b) {
        return compare(a, b) < 0;
    }

    public static boolean lessOrEquals(byte[] a, byte[] b) {
        return compare(a, b) <= 0;
    }

    public static boolean greaterThan(byte[] a, byte[] b) {
        return compare(a, b) > 0;
    }

    public static boolean greaterOrEquals(byte[] a, byte[] b) {
        return compare(a, b) >= 0;
    }
}
