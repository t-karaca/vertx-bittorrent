package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString
public class HaveMessage extends Message {
    private final int pieceIndex;

    @Override
    public int getPayloadLength() {
        return 4;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.HAVE;
    }

    @Override
    protected void appendPayload(Buffer buffer) {
        buffer.appendInt(pieceIndex);
    }

    public static HaveMessage fromBuffer(ByteBuffer buffer) {
        int pieceIndex = buffer.getInt();

        return new HaveMessage(pieceIndex);
    }
}
