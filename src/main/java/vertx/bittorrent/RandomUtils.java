package vertx.bittorrent;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

public final class RandomUtils {
    private static final Random RANDOM = new SecureRandom();

    private RandomUtils() {}

    public static <T> T randomFrom(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        return list.get(RANDOM.nextInt(list.size()));
    }

    public static <T> T reservoirSample(T a, T b) {
        return RANDOM.nextBoolean() ? a : b;
    }
}
