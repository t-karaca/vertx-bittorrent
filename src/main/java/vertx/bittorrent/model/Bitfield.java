package vertx.bittorrent.model;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Bitfield {

    private final BitSet bits;
    private final int size;

    public boolean hasAnyPieces() {
        for (int i = 0; i < bits.length(); i++) {
            if (bits.get(i)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasPiece(int index) {
        return bits.get(index);
    }

    public void setPiece(int index) {
        bits.set(index);
    }

    public int nextMissingPiece(int fromIndex) {
        return bits.nextClearBit(fromIndex);
    }

    public int cardinality() {
        return bits.cardinality();
    }

    public int getByteCount() {
        return (size + 7) / 8;
    }

    public byte[] toByteArray() {
        // needs to be big-endian (BitSet methods work in little-endian)
        byte[] bytes = new byte[getByteCount()];
        for (int i = 0; i < size; i++) {
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
        return new Bitfield(new BitSet(size), size);
    }

    public static Bitfield fromBytes(byte[] bytes) {
        return fromBytes(bytes, 0, bytes.length);
    }

    public static Bitfield fromBytes(byte[] bytes, int offset, int length) {
        int bitOffset = offset * 8;
        int bitLength = length * 8;

        BitSet bitset = new BitSet(bitLength);

        for (int i = 0; i < bitLength; i++) {
            int arrayOffset = i + bitOffset;
            if ((bytes[arrayOffset / 8] & (1 << (7 - arrayOffset % 8))) > 0) {
                bitset.set(i);
            }
        }

        return new Bitfield(bitset, bitLength);
    }
}
