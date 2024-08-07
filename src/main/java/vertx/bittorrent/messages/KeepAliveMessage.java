package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;

public class KeepAliveMessage extends Message {

    @Override
    public String toString() {
        return "KeepAliveMessage()";
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.KEEP_ALIVE;
    }

    @Override
    public Buffer toBuffer() {
        Buffer buffer = Buffer.buffer(4);

        return buffer;
    }
}
