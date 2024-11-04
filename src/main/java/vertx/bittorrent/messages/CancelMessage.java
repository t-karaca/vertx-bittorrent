package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CancelMessage extends Message {
    private final int pieceIndex;
    private final int begin;
    private final int length;

    @Override
    public int getPayloadLength() {
        return 12;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.CANCEL;
    }

    @Override
    protected void appendPayload(Buffer buffer) {
        buffer.appendInt(pieceIndex);
        buffer.appendInt(begin);
        buffer.appendInt(length);
    }

    public static CancelMessage fromBuffer(ByteBuffer buffer) {
        int pieceIndex = buffer.getInt();
        int begin = buffer.getInt();
        int length = buffer.getInt();

        return new CancelMessage(pieceIndex, begin, length);
    }
}
