package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.Bitfield;

public class BitfieldTest {
    @Test
    @DisplayName("should parse bitfield")
    void bitfieldTest() {
        Bitfield bitfield = Bitfield.fromBytes(new byte[] {-1, 0, 1});

        assertThat(bitfield.hasPiece(0)).isTrue();
        assertThat(bitfield.hasPiece(1)).isTrue();
        assertThat(bitfield.hasPiece(2)).isTrue();
        assertThat(bitfield.hasPiece(3)).isTrue();
        assertThat(bitfield.hasPiece(4)).isTrue();
        assertThat(bitfield.hasPiece(5)).isTrue();
        assertThat(bitfield.hasPiece(6)).isTrue();
        assertThat(bitfield.hasPiece(7)).isTrue();
        assertThat(bitfield.hasPiece(8)).isFalse();
        assertThat(bitfield.hasPiece(9)).isFalse();
        assertThat(bitfield.hasPiece(10)).isFalse();
        assertThat(bitfield.hasPiece(11)).isFalse();
        assertThat(bitfield.hasPiece(12)).isFalse();
        assertThat(bitfield.hasPiece(13)).isFalse();
        assertThat(bitfield.hasPiece(14)).isFalse();
        assertThat(bitfield.hasPiece(15)).isFalse();
        assertThat(bitfield.hasPiece(16)).isFalse();
        assertThat(bitfield.hasPiece(17)).isFalse();
        assertThat(bitfield.hasPiece(18)).isFalse();
        assertThat(bitfield.hasPiece(19)).isFalse();
        assertThat(bitfield.hasPiece(20)).isFalse();
        assertThat(bitfield.hasPiece(21)).isFalse();
        assertThat(bitfield.hasPiece(22)).isFalse();
        assertThat(bitfield.hasPiece(23)).isTrue();
    }

    @Test
    @DisplayName("should write bitfield to bytes")
    void bitfieldBytesTest() {
        Bitfield bitfield = Bitfield.fromSize(32);

        bitfield.setPiece(0);
        bitfield.setPiece(17);
        bitfield.setPiece(22);
        bitfield.setPiece(29);

        byte[] bytes = bitfield.toByteArray();

        assertThat(bytes).hasSize(4);
        assertThat(bytes[0]).isEqualTo((byte) (0x80 & 0xFF));
        assertThat(bytes[1]).isEqualTo((byte) (0x00 & 0xFF));
        assertThat(bytes[2]).isEqualTo((byte) (0x42 & 0xFF));
        assertThat(bytes[3]).isEqualTo((byte) (0x04 & 0xFF));
    }
}
