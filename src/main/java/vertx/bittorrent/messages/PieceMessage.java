package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PieceMessage extends Message {
    private final int pieceIndex;
    private final int begin;

    private final Buffer data;

    @Override
    public int getPayloadLength() {
        return 8 + data.length();
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.PIECE;
    }

    @Override
    protected void appendPayload(Buffer buffer) {
        buffer.appendInt(pieceIndex);
        buffer.appendInt(begin);
        buffer.appendBuffer(data);
    }

    @Override
    public String toString() {
        return "PieceMessage(pieceIndex=" + pieceIndex + ", begin=" + begin + ", data.length=" + data.length() + ")";
    }

    public static PieceMessage fromBuffer(ByteBuffer buffer) {
        int pieceIndex = buffer.getInt();
        int begin = buffer.getInt();

        int dataLength = buffer.remaining();
        Buffer data = Buffer.buffer(dataLength);
        data.setBytes(0, buffer.array(), buffer.position(), dataLength);

        buffer.position(buffer.position() + dataLength);

        return new PieceMessage(pieceIndex, begin, data);
    }
}
