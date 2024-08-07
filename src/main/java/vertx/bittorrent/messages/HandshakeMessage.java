package vertx.bittorrent.messages;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class HandshakeMessage extends Message {
    public static final int HANDSHAKE_LENGTH = 68;
    private static final String PROTOCOL_NAME = "BitTorrent protocol";

    private final long reserved;
    private final byte[] infoHash;
    private final byte[] peerId;

    @Override
    public String toString() {
        return "HandshakeMessage(reserved=%016X, infoHash=%s, peerId=%s)"
                .formatted(
                        reserved,
                        HexFormat.of().formatHex(infoHash),
                        HexFormat.of().formatHex(peerId));
    }

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
        byte protocolNameLength = buffer.get();
        if (protocolNameLength != PROTOCOL_NAME.length()) {
            return null;
        }

        byte[] protocolNameBytes = new byte[PROTOCOL_NAME.length()];
        buffer.get(protocolNameBytes);

        if (!PROTOCOL_NAME.equals(new String(protocolNameBytes, StandardCharsets.UTF_8))) {
            return null;
        }

        // reserved bytes
        long reserved = buffer.getLong();

        byte[] infoHash = new byte[20];
        buffer.get(infoHash);

        byte[] peerId = new byte[20];
        buffer.get(peerId);

        return new HandshakeMessage(reserved, infoHash, peerId);
    }
}
