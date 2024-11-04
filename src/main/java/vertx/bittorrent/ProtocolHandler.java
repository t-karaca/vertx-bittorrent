package vertx.bittorrent;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.messages.BitfieldMessage;
import vertx.bittorrent.messages.CancelMessage;
import vertx.bittorrent.messages.ChokeMessage;
import vertx.bittorrent.messages.HandshakeMessage;
import vertx.bittorrent.messages.HaveMessage;
import vertx.bittorrent.messages.InterestedMessage;
import vertx.bittorrent.messages.KeepAliveMessage;
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
    private int remainingLengthBytes = 0;

    private Handler<Message> messageHandler;
    private Handler<Void> invalidHandshakeHandler;

    public ProtocolHandler onMessage(Handler<Message> handler) {
        messageHandler = handler;
        return this;
    }

    public ProtocolHandler onInvalidHandshake(Handler<Void> handler) {
        invalidHandshakeHandler = handler;
        return this;
    }

    public void skipHandshake() {
        handshakeComplete = true;
    }

    public void reset() {
        input.limit(0);

        handshakeComplete = false;
        nextMessageLength = -1;
        remainingLengthBytes = 0;
    }

    public void readBuffer(Buffer buffer) {
        int bufferPosition = 0;

        while (buffer.length() > bufferPosition) {

            if (nextMessageLength == -1) {
                if (!handshakeComplete) {
                    nextMessageLength = HandshakeMessage.HANDSHAKE_LENGTH;
                } else {
                    int remaining = buffer.length() - bufferPosition;

                    // if (remainingLengthBytes == 4 && remainingLengthBytes <= remaining) {
                    //     nextMessageLength = buffer.getInt(bufferPosition);
                    //     bufferPosition += 4;
                    // }

                    if (remainingLengthBytes > 0) {
                        if (remaining < remainingLengthBytes) {
                            // log.debug("Still remaining bytes {} < {}", remaining, remainingLengthBytes);
                            remainingLengthBytes -= remaining;

                            buffer.getBytes(bufferPosition, bufferPosition + remaining, input.array(), input.limit());
                            input.limit(input.limit() + remaining);
                            bufferPosition += remaining;
                        } else {
                            // log.debug("Got all bytes with {}", remainingLengthBytes);
                            buffer.getBytes(
                                    bufferPosition,
                                    bufferPosition + remainingLengthBytes,
                                    input.array(),
                                    input.limit());
                            input.limit(input.limit() + remainingLengthBytes);
                            bufferPosition += remainingLengthBytes;

                            nextMessageLength = input.getInt();
                            input.limit(0);
                            remainingLengthBytes = 0;
                        }
                    } else if (remaining < 4) {
                        // log.debug("Remaining bytes {} < 4", remaining);
                        remainingLengthBytes = 4 - remaining;

                        buffer.getBytes(bufferPosition, bufferPosition + remaining, input.array(), input.limit());
                        input.limit(input.limit() + remaining);
                        bufferPosition += remaining;
                    } else {
                        nextMessageLength = buffer.getInt(bufferPosition);
                        bufferPosition += 4;
                    }
                }
            }

            if (nextMessageLength == 0) {
                if (messageHandler != null) {
                    messageHandler.handle(new KeepAliveMessage());
                }

                nextMessageLength = -1;
                input.limit(0);
            } else if (nextMessageLength > 0) {

                int bytesToRead = nextMessageLength - input.limit();

                int readableBytes = Math.min(buffer.length() - bufferPosition, bytesToRead);

                if (readableBytes <= 0) {
                    return;
                }

                buffer.getBytes(bufferPosition, bufferPosition + readableBytes, input.array(), input.limit());
                bufferPosition += readableBytes;
                input.limit(input.limit() + readableBytes);

                if (input.limit() == nextMessageLength) {
                    Message message = null;

                    if (!handshakeComplete) {
                        message = HandshakeMessage.fromBuffer(input);

                        if (message == null) {
                            log.debug("Received invalid handshake");

                            if (invalidHandshakeHandler != null) {
                                invalidHandshakeHandler.handle(null);
                            }

                            reset();
                            return;
                        }

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
                            case CANCEL -> CancelMessage.fromBuffer(input);
                            default -> null;};
                    }

                    if (message != null) {
                        if (messageHandler != null) {
                            messageHandler.handle(message);
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
}
