package vertx.bittorrent;

import lombok.Getter;

@Getter
public class PieceState {
    public enum BlockState {
        Queued,
        Requested,
        Downloaded
    }

    private final BlockState[] blockStates;

    public PieceState(long pieceLength) {
        int blocksCount = (int) ((pieceLength + ProtocolHandler.MAX_BLOCK_SIZE - 1) / ProtocolHandler.MAX_BLOCK_SIZE);

        blockStates = new BlockState[blocksCount];

        for (int i = 0; i < blocksCount; i++) {
            blockStates[i] = BlockState.Queued;
        }
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

    public boolean isCompleted() {
        for (int i = 0; i < getBlocksCount(); i++) {
            if (getBlockState(i) != BlockState.Downloaded) {
                return false;
            }
        }

        return true;
    }
}
