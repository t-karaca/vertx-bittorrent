package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import vertx.bittorrent.Bitfield;

@Getter
@RequiredArgsConstructor
@ToString
public class BitfieldMessage extends Message {
    @NonNull
    private final Bitfield bitfield;

    @Override
    public int getPayloadLength() {
        return bitfield.getByteCount();
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BITFIELD;
    }

    @Override
    public void appendPayload(Buffer buffer) {
        buffer.appendBytes(bitfield.toByteArray());
    }

    public static BitfieldMessage fromBuffer(ByteBuffer buffer) {
        return new BitfieldMessage(Bitfield.fromBytes(buffer.array(), buffer.position(), buffer.limit()));
    }
}
