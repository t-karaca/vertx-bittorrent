package vertx.bittorrent.utils;

import java.text.DecimalFormat;

public final class ByteFormat {
    private static final long BYTE = 1L;
    private static final long KILO_BYTE = BYTE << 10;
    private static final long MEGA_BYTE = KILO_BYTE << 10;
    private static final long GIGA_BYTE = MEGA_BYTE << 10;
    private static final long TERA_BYTE = GIGA_BYTE << 10;
    private static final long PETA_BYTE = TERA_BYTE << 10;

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    public static String format(double length) {
        if (length >= PETA_BYTE) {
            return format(length, PETA_BYTE, "PB");
        }

        if (length >= TERA_BYTE) {
            return format(length, TERA_BYTE, "TB");
        }

        if (length >= GIGA_BYTE) {
            return format(length, GIGA_BYTE, "GB");
        }

        if (length >= MEGA_BYTE) {
            return format(length, MEGA_BYTE, "MB");
        }

        if (length >= KILO_BYTE) {
            return format(length, KILO_BYTE, "KB");
        }

        return format(length, BYTE, "B");
    }

    private static String format(double length, long divider, String unit) {
        return DECIMAL_FORMAT.format(length / divider) + " " + unit;
    }
}
