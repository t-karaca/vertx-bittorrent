package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import java.util.Base64;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vertx.bittorrent.ProtocolHandler;
import vertx.bittorrent.messages.BitfieldMessage;
import vertx.bittorrent.messages.ChokeMessage;
import vertx.bittorrent.messages.HandshakeMessage;
import vertx.bittorrent.messages.HaveMessage;
import vertx.bittorrent.messages.InterestedMessage;
import vertx.bittorrent.messages.Message;
import vertx.bittorrent.messages.NotInterestedMessage;
import vertx.bittorrent.messages.PieceMessage;
import vertx.bittorrent.messages.RequestMessage;
import vertx.bittorrent.messages.UnchokeMessage;

@SuppressWarnings("unchecked")
public class ProtocolHandlerTest {

    @Test
    void testHandshake() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);

        Buffer buffer = fromBase64(
                "E0JpdFRvcnJlbnQgcHJvdG9jb2wAAAAAABAABIHnU8XnV5/2CNdqoTiH0NCM/ecCQTItMS0zNy0wLbUFa464yB4/o5c=");

        protocolHandler.readBuffer(buffer);

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(HandshakeMessage.class);
    }

    @Test
    void testInvalidHandshake() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);
        Handler<Void> invalidHandshakeHandler = (Handler<Void>) mock(Handler.class);

        ProtocolHandler protocolHandler =
                new ProtocolHandler().onMessage(handler).onInvalidHandshake(invalidHandshakeHandler);

        Buffer buffer = fromBase64(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/2CNdqoTiH0NCM/ecCQTItMS0zNy0wLbUFa464yB4/o5c=");

        protocolHandler.readBuffer(buffer);

        verify(handler, times(0)).handle(any());
        verify(invalidHandshakeHandler, times(1)).handle(any());
    }

    @Test
    @Disabled
    void testInvalidHandshake2() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);
        Handler<Void> invalidHandshakeHandler = (Handler<Void>) mock(Handler.class);

        ProtocolHandler protocolHandler =
                new ProtocolHandler().onMessage(handler).onInvalidHandshake(invalidHandshakeHandler);

        Buffer buffer = fromBase64("2CNdqoTiH0NCM/ecCQTItMS0zNy0wLbUFa464yB4/o5c");

        protocolHandler.readBuffer(buffer);

        verify(handler, times(0)).handle(any());
        verify(invalidHandshakeHandler, times(1)).handle(any());
    }

    @Test
    void testChokeMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 1, 0}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(ChokeMessage.class);
    }

    @Test
    void testUnchokeMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 1, 1}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(UnchokeMessage.class);
    }

    @Test
    void testInterestedMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 1, 2}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(InterestedMessage.class);
    }

    @Test
    void testNotInterestedMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 1, 3}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(NotInterestedMessage.class);
    }

    @Test
    void testHaveMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 5, 4, 0, 0, 1, 0}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOfSatisfying(HaveMessage.class, msg -> {
            assertThat(msg.getPieceIndex()).isEqualTo(256);
        });
    }

    @Test
    void testBitfieldMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 3, 5, 0, -1}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOfSatisfying(BitfieldMessage.class, msg -> {
            assertThat(msg.getBitfield().hasPiece(0)).isFalse();
            assertThat(msg.getBitfield().hasPiece(1)).isFalse();
            assertThat(msg.getBitfield().hasPiece(7)).isFalse();
            assertThat(msg.getBitfield().hasPiece(8)).isTrue();
            assertThat(msg.getBitfield().hasPiece(15)).isTrue();
        });
    }

    @Test
    void testRequestMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 13, 6, 0, 0, 0, 4, 0, 0, 0, 16, 0, 0, 0, 12}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOfSatisfying(RequestMessage.class, msg -> {
            assertThat(msg.getPieceIndex()).isEqualTo(4);
            assertThat(msg.getBegin()).isEqualTo(16);
            assertThat(msg.getLength()).isEqualTo(12);
        });
    }

    @Test
    void testPieceMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(
                Buffer.buffer(new byte[] {0, 0, 0, 14, 7, 0, 0, 0, 3, 0, 0, 0, 12, 0, 0, 0, 11, 10}));
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getValue()).isInstanceOfSatisfying(PieceMessage.class, msg -> {
            assertThat(msg.getPieceIndex()).isEqualTo(3);
            assertThat(msg.getBegin()).isEqualTo(12);
            assertThat(msg.getData().getBytes()).containsExactly(0, 0, 0, 11, 10);
        });
    }

    @Test
    void testMultipleMessages() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(
                Buffer.buffer(new byte[] {0, 0, 0, 14, 7, 0, 0, 0, 3, 0, 0, 0, 12, 0, 0, 0, 11, 10, 0, 0, 0, 1, 1}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler, times(2)).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(PieceMessage.class, msg -> {
            assertThat(msg.getPieceIndex()).isEqualTo(3);
            assertThat(msg.getBegin()).isEqualTo(12);
            assertThat(msg.getData().getBytes()).containsExactly(0, 0, 0, 11, 10);
        });

        assertThat(captor.getAllValues().get(1)).isInstanceOf(UnchokeMessage.class);
    }

    @Test
    void testPartialMessage() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 14, 7, 0, 0, 0, 3, 0, 0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 12, 0, 0, 0, 11, 10}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(PieceMessage.class, msg -> {
            assertThat(msg.getPieceIndex()).isEqualTo(3);
            assertThat(msg.getBegin()).isEqualTo(12);
            assertThat(msg.getData().getBytes()).containsExactly(0, 0, 0, 11, 10);
        });
    }

    @Test
    void testPartialMessage2() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0, 14, 7, 0, 0, 0, 3, 0, 0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 12, 0, 0, 0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {11, 10}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(PieceMessage.class, msg -> {
            assertThat(msg.getPieceIndex()).isEqualTo(3);
            assertThat(msg.getBegin()).isEqualTo(12);
            assertThat(msg.getData().getBytes()).containsExactly(0, 0, 0, 11, 10);
        });
    }

    @Test
    void testPartialMessageLength() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {1}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {2}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(InterestedMessage.class);
    }

    @Test
    void testPartialMessageLength2() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {1}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {2}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(InterestedMessage.class);
    }

    @Test
    void testPartialMessageLength3() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 1}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {2}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(InterestedMessage.class);
    }

    @Test
    void testPartialMessageLength4() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 1, 2}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(InterestedMessage.class);
    }

    @Test
    void testPartialMessageLength5() {
        Handler<Message> handler = (Handler<Message>) mock(Handler.class);

        ProtocolHandler protocolHandler = new ProtocolHandler().onMessage(handler);
        protocolHandler.skipHandshake();

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {0, 0, 0}));

        verify(handler, times(0)).handle(any());

        protocolHandler.readBuffer(Buffer.buffer(new byte[] {1, 2}));

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(handler).handle(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(InterestedMessage.class);
    }

    static Buffer fromBase64(String base64) {
        return Buffer.buffer(Base64.getDecoder().decode(base64));
    }
}
