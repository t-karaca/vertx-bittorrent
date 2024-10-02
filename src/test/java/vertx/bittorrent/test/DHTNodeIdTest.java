package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.dht.DHTNodeId;

public class DHTNodeIdTest {
    @Test
    void toByteArray() {
        assertThat(DHTNodeId.fromHex("0000ff67187243125789abcdef1234567890abcd").getBytes())
                .asHexString()
                .isEqualToIgnoringCase("0000ff67187243125789abcdef1234567890abcd");

        assertThat(DHTNodeId.fromHex("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055").getBytes())
                .asHexString()
                .isEqualToIgnoringCase("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055");
    }

    @Test
    void fromByteArray() {
        assertThat(new DHTNodeId(HexFormat.of().parseHex("0000ff67187243125789abcdef1234567890abcd")).toString())
                .isEqualToIgnoringCase("0000ff67187243125789abcdef1234567890abcd");

        assertThat(new DHTNodeId(HexFormat.of().parseHex("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055")).toString())
                .isEqualToIgnoringCase("f489b26f5a2b9e2d3536a8e4f83f8fa8e0401055");
    }

    @Test
    void compare() {
        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .equals(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isTrue();

        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .lessOrEquals(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isTrue();

        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .greaterOrEquals(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isTrue();

        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .lessThan(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isFalse();

        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .greaterThan(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")))
                .isFalse();

        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .equals(DHTNodeId.fromHex("0ab494599df99ef0eb16722f355f604d6a59e831")))
                .isFalse();

        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .lessThan(DHTNodeId.fromHex("0ab494599df99ef0eb16722f355f604d6a59e831")))
                .isTrue();

        assertThat(DHTNodeId.fromHex("09b7fb7fd29b90349f31a6a389c2f85089eb685c")
                        .greaterThan(DHTNodeId.fromHex("0ab494599df99ef0eb16722f355f604d6a59e831")))
                .isFalse();

        assertThat(DHTNodeId.fromHex("0400").greaterThan(DHTNodeId.fromHex("0200")))
                .isTrue();
        assertThat(DHTNodeId.fromHex("0400").greaterThan(DHTNodeId.fromHex("0800")))
                .isFalse();
        assertThat(DHTNodeId.fromHex("0400").lessThan(DHTNodeId.fromHex("0800")))
                .isTrue();
        assertThat(DHTNodeId.fromHex("0400").lessThan(DHTNodeId.MAX)).isTrue();
    }

    @Test
    void random() {
        for (int i = 0; i < 1000; i++) {
            DHTNodeId value = DHTNodeId.random();

            assertThat(value.lessThan(DHTNodeId.MAX)).isTrue();
        }
    }

    @Test
    void randomRange() {
        DHTNodeId min = DHTNodeId.fromHex("638a600000000000000000000000000000000000");
        DHTNodeId max = DHTNodeId.fromHex("638a700000000000000000000000000000000000");

        for (int i = 0; i < 1000; i++) {
            DHTNodeId value = DHTNodeId.random(min, max);

            assertThat(value.greaterOrEquals(min)).isTrue();
            assertThat(value.lessThan(max)).isTrue();
        }
    }
}
