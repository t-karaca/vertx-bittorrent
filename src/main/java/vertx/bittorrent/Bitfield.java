package vertx.bittorrent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Bitfield {

    private final BitSet bits;

    public boolean hasPiece(int index) {
        return bits.get(index);
    }

    public void setPiece(int index) {
        bits.set(index);
    }

    public int cardinality() {
        return bits.cardinality();
    }

    public int getByteCount() {
        return (bits.length() + 7) / 8;
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[getByteCount()];
        for (int i = 0; i < bits.length(); i++) {
            if (bits.get(i)) {
                bytes[i / 8] |= 1 << (7 - i % 8);
            }
        }
        return bytes;
    }

    public List<Integer> getMissingPieces() {
        List<Integer> missingPieces = new ArrayList<>();
        for (int i = 0; i < bits.length(); i++) {
            if (!bits.get(i)) {
                missingPieces.add(i);
            }
        }
        return missingPieces;
    }

    public static Bitfield fromSize(int size) {
        return new Bitfield(new BitSet(size));
    }

    public static Bitfield fromBytes(byte[] bytes) {
        return fromBytes(bytes, 0, bytes.length);
    }

    public static Bitfield fromBytes(byte[] bytes, int offset, int length) {
        BitSet bitset = new BitSet((length - offset) * 8);

        for (int i = offset * 8; i < (offset + length) * 8; i++) {
            if ((bytes[i / 8] & (1 << (7 - i % 8))) > 0) {
                bitset.set(i);
            }
        }

        return new Bitfield(bitset);
    }
}
