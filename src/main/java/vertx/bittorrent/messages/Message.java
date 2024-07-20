package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;

public class Message {

    public int getPayloadLength() {
        return 0;
    }

    public MessageType getMessageType() {
        return MessageType.KEEP_ALIVE;
    }

    public Buffer toBuffer() {
        Buffer buffer = Buffer.buffer(getPayloadLength() + 5);
        buffer.appendInt(getPayloadLength() + 1);
        buffer.appendByte((byte) getMessageType().getValue());
        appendPayload(buffer);
        return buffer;
    }

    protected void appendPayload(Buffer buffer) {}
}
