package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString
public class RequestMessage extends Message {
    private final int pieceIndex;
    private final int begin;
    private final int length;

    @Override
    public int getPayloadLength() {
        return 12;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.REQUEST;
    }

    @Override
    protected void appendPayload(Buffer buffer) {
        buffer.appendInt(pieceIndex);
        buffer.appendInt(begin);
        buffer.appendInt(length);
    }

    public static RequestMessage fromBuffer(ByteBuffer buffer) {
        int pieceIndex = buffer.getInt();
        int begin = buffer.getInt();
        int length = buffer.getInt();

        return new RequestMessage(pieceIndex, begin, length);
    }
}
