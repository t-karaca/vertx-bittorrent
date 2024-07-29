package vertx.bittorrent;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.messages.BitfieldMessage;
import vertx.bittorrent.messages.ChokeMessage;
import vertx.bittorrent.messages.HandshakeMessage;
import vertx.bittorrent.messages.HaveMessage;
import vertx.bittorrent.messages.InterestedMessage;
import vertx.bittorrent.messages.Message;
import vertx.bittorrent.messages.MessageType;
import vertx.bittorrent.messages.NotInterestedMessage;
import vertx.bittorrent.messages.PieceMessage;
import vertx.bittorrent.messages.RequestMessage;
import vertx.bittorrent.messages.UnchokeMessage;

@Slf4j
public class ProtocolHandler {
    public static final int MAX_BLOCK_SIZE = 16384;

    private final ByteBuffer input = ByteBuffer.allocate(MAX_BLOCK_SIZE * 2).limit(0);

    private boolean handshakeComplete = false;

    private int nextMessageLength = -1;

    @Setter
    private Consumer<Message> messageHandler;

    public void reset() {
        input.limit(0);

        handshakeComplete = false;
        nextMessageLength = -1;
    }

    public void readBuffer(Buffer buffer) {
        int bufferPosition = 0;

        while (buffer.length() > bufferPosition) {

            if (nextMessageLength == -1) {
                if (!handshakeComplete) {
                    nextMessageLength = HandshakeMessage.HANDSHAKE_LENGTH;
                } else {
                    nextMessageLength = buffer.getInt(bufferPosition);
                    bufferPosition += 4;
                }
            }

            int bytesToRead = nextMessageLength - input.limit();

            int readableBytes = Math.min(buffer.length() - bufferPosition, bytesToRead);

            buffer.getBytes(bufferPosition, bufferPosition + readableBytes, input.array(), input.limit());
            bufferPosition += readableBytes;
            input.limit(input.limit() + readableBytes);

            if (input.limit() == nextMessageLength) {
                Message message = null;

                if (!handshakeComplete) {
                    message = HandshakeMessage.fromBuffer(input);

                    handshakeComplete = true;
                } else {
                    MessageType messageType = MessageType.fromValue(input.get());

                    message = switch (messageType) {
                        case CHOKE -> new ChokeMessage();
                        case UNCHOKE -> new UnchokeMessage();
                        case INTERESTED -> new InterestedMessage();
                        case NOT_INTERESTED -> new NotInterestedMessage();
                        case HAVE -> HaveMessage.fromBuffer(input);
                        case BITFIELD -> BitfieldMessage.fromBuffer(input);
                        case REQUEST -> RequestMessage.fromBuffer(input);
                        case PIECE -> PieceMessage.fromBuffer(input);
                        default -> null;};
                }

                if (message != null) {
                    if (messageHandler != null) {
                        messageHandler.accept(message);
                    }
                } else {
                    log.debug("Received unknown message");
                }

                input.limit(0);
                nextMessageLength = -1;
            }
        }
    }
}
