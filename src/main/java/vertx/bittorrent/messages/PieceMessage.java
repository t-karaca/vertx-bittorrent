package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PieceMessage extends Message {
    private final int pieceIndex;
    private final int begin;

    @NonNull
    private final byte[] data;

    @Override
    public int getPayloadLength() {
        return 8 + data.length;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.PIECE;
    }

    @Override
    protected void appendPayload(Buffer buffer) {
        buffer.appendInt(pieceIndex);
        buffer.appendInt(begin);
        buffer.appendBytes(data);
    }

    @Override
    public String toString() {
        return "PieceMessage(pieceIndex=" + pieceIndex + ", begin=" + begin + ", data.length=" + data.length + ")";
    }

    public static PieceMessage fromBuffer(ByteBuffer buffer) {
        int pieceIndex = buffer.getInt();
        int begin = buffer.getInt();

        byte[] data = new byte[buffer.limit() - buffer.position()];
        buffer.get(data);

        return new PieceMessage(pieceIndex, begin, data);
    }
}
