package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString
public class HandshakeMessage extends Message {
    public static final int HANDSHAKE_LENGTH = 68;
    private static final String PROTOCOL_NAME = "BitTorrent protocol";

    private final byte[] infoHash;
    private final byte[] peerId;

    @Override
    public MessageType getMessageType() {
        return MessageType.HANDSHAKE;
    }

    @Override
    public Buffer toBuffer() {
        Buffer buffer = Buffer.buffer(HANDSHAKE_LENGTH);

        buffer.appendByte((byte) PROTOCOL_NAME.length());
        buffer.appendString(PROTOCOL_NAME);
        buffer.appendLong(0L);
        buffer.appendBytes(infoHash);
        buffer.appendBytes(peerId);

        return buffer;
    }

    public static HandshakeMessage fromBuffer(ByteBuffer buffer) {
        if (buffer.capacity() < HANDSHAKE_LENGTH) {
            throw new IllegalArgumentException("Buffer has insufficient capacity for a handshake message");
        }

        // protocol name length
        buffer.get();

        byte[] protocolNameBytes = new byte[PROTOCOL_NAME.length()];
        buffer.get(protocolNameBytes);

        // reserved bytes
        buffer.getLong();

        byte[] infoHash = new byte[20];
        buffer.get(infoHash);

        byte[] peerId = new byte[20];
        buffer.get(peerId);

        return new HandshakeMessage(infoHash, peerId);
    }
}
