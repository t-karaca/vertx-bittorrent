package vertx.bittorrent;

import io.vertx.core.buffer.Buffer;
import lombok.Getter;

public class PieceState {
    public enum BlockState {
        Queued,
        Requested,
        Downloaded
    }

    @Getter
    private final long pieceLength;

    @Getter
    private final BlockState[] blockStates;

    private Buffer data;

    public PieceState(long pieceLength) {
        this.pieceLength = pieceLength;

        int blocksCount = (int) ((pieceLength + ProtocolHandler.MAX_BLOCK_SIZE - 1) / ProtocolHandler.MAX_BLOCK_SIZE);

        blockStates = new BlockState[blocksCount];

        for (int i = 0; i < blocksCount; i++) {
            blockStates[i] = BlockState.Queued;
        }
    }

    public Buffer getData() {
        if (data == null) {
            // lazy allocation
            data = Buffer.buffer((int) pieceLength);
        }

        return data;
    }

    public int getBlocksCount() {
        return blockStates.length;
    }

    public BlockState getBlockState(int index) {
        return blockStates[index];
    }

    public BlockState getBlockStateByOffset(int offset) {
        return blockStates[offset / ProtocolHandler.MAX_BLOCK_SIZE];
    }

    public void setBlockState(int index, BlockState state) {
        blockStates[index] = state;
    }

    public void setBlockStateByOffset(int offset, BlockState state) {
        blockStates[offset / ProtocolHandler.MAX_BLOCK_SIZE] = state;
    }

    public int getBlockOffset(int index) {
        return ProtocolHandler.MAX_BLOCK_SIZE * index;
    }

    public int getBlockSize(int index) {
        int blockSize = ProtocolHandler.MAX_BLOCK_SIZE;

        if (index == blockStates.length - 1) {
            blockSize = (int) (pieceLength - index * blockSize);
        }

        return blockSize;
    }

    public boolean isCompleted() {
        for (int i = 0; i < getBlocksCount(); i++) {
            if (getBlockState(i) != BlockState.Downloaded) {
                return false;
            }
        }

        return true;
    }
}
