package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.dht.HashKey;

public class HashKeyTest {
    @Test
    void toByteArray() {
        assertThat(HashKey.fromHex("0000ff67187243125789abcdef1234567890abcd").getBytes())
                .asHexString()
                .isEqualToIgnoringCase("0000ff67187243125789abcdef1234567890abcd");

        assertThat(HashKey.fromHex("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055").getBytes())
                .asHexString()
                .isEqualToIgnoringCase("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055");
    }

    @Test
    void fromByteArray() {
        assertThat(new HashKey(HexFormat.of().parseHex("0000ff67187243125789abcdef1234567890abcd")).toString())
                .isEqualToIgnoringCase("0000ff67187243125789abcdef1234567890abcd");

        assertThat(new HashKey(HexFormat.of().parseHex("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055")).toString())
                .isEqualToIgnoringCase("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055");
    }

    @Test
    void compare() {
        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .equals(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isTrue();

        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .lessOrEquals(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isTrue();

        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .greaterOrEquals(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isTrue();

        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .lessThan(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isFalse();

        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .greaterThan(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isFalse();

        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .equals(HashKey.fromHex("0ab494599df99ef0eb16722f355f604d6a59e831")))
                .isFalse();

        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .lessThan(HashKey.fromHex("0ab494599df99ef0eb16722f355f604d6a59e831")))
                .isTrue();

        assertThat(HashKey.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .greaterThan(HashKey.fromHex("0ab494599df99ef0eb16722f355f604d6a59e831")))
                .isFalse();

        assertThat(HashKey.fromHex("0400").greaterThan(HashKey.fromHex("0200"))).isTrue();
        assertThat(HashKey.fromHex("0400").greaterThan(HashKey.fromHex("0800"))).isFalse();
        assertThat(HashKey.fromHex("0400").lessThan(HashKey.fromHex("0800"))).isTrue();
        assertThat(HashKey.fromHex("0400").lessThan(HashKey.MAX)).isTrue();
    }

    @Test
    void random() {
        for (int i = 0; i < 1000; i++) {
            HashKey value = HashKey.random();

            assertThat(value.lessThan(HashKey.MAX)).isTrue();
        }
    }

    @Test
    void randomRange() {
        HashKey min = HashKey.fromHex("638a600000000000000000000000000000000000");
        HashKey max = HashKey.fromHex("638a700000000000000000000000000000000000");

        for (int i = 0; i < 1000; i++) {
            HashKey value = HashKey.random(min, max);

            assertThat(value.greaterOrEquals(min)).isTrue();
            assertThat(value.lessThan(max)).isTrue();
        }
    }
}
