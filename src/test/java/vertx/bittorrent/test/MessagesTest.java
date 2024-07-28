package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.Bitfield;
import vertx.bittorrent.messages.BitfieldMessage;
import vertx.bittorrent.messages.ChokeMessage;
import vertx.bittorrent.messages.HandshakeMessage;
import vertx.bittorrent.messages.InterestedMessage;
import vertx.bittorrent.messages.NotInterestedMessage;
import vertx.bittorrent.messages.PieceMessage;
import vertx.bittorrent.messages.RequestMessage;
import vertx.bittorrent.messages.UnchokeMessage;

public class MessagesTest {
    @Test
    @DisplayName("should parse handshake message")
    void handshakeMessageTest() {
        // handshake contains extensions
        ByteBuffer buffer = fromBase64(
                "E0JpdFRvcnJlbnQgcHJvdG9jb2wAAAAAABAABIHnU8XnV5/2CNdqoTiH0NCM/ecCQTItMS0zNy0wLbUFa464yB4/o5c=");

        HandshakeMessage message = HandshakeMessage.fromBuffer(buffer);

        assertThat(message.getInfoHash()).asBase64Encoded().isEqualTo("gedTxedXn/YI12qhOIfQ0Iz95wI=");
        assertThat(message.getPeerId()).asBase64Encoded().isEqualTo("QTItMS0zNy0wLbUFa464yB4/o5c=");
    }

    @Test
    @DisplayName("should write handshake message")
    void writeHandshakeMessageTest() {
        byte[] infoHash = Base64.getDecoder().decode("gedTxedXn/YI12qhOIfQ0Iz95wI=");
        byte[] peerId = Base64.getDecoder().decode("QTItMS0zNy0wLbUFa464yB4/o5c=");

        HandshakeMessage message = new HandshakeMessage(infoHash, peerId);

        byte[] bytes = message.toBuffer().getBytes();

        // handshake does NOT contain extensions
        assertThat(bytes)
                .asBase64Encoded()
                .isEqualTo(
                        "E0JpdFRvcnJlbnQgcHJvdG9jb2wAAAAAAAAAAIHnU8XnV5/2CNdqoTiH0NCM/ecCQTItMS0zNy0wLbUFa464yB4/o5c=");
    }

    @Test
    @DisplayName("should parse bitfield message")
    void bitfieldMessageTest() {
        ByteBuffer buffer = fromBase64(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAf////////////////////////////////8=");

        var message = BitfieldMessage.fromBuffer(buffer);

        assertThat(message.getBitfield().getByteCount()).isEqualTo(125);
        assertThat(message.getBitfield().hasPiece(0)).isFalse();
        assertThat(message.getBitfield().hasPiece(10)).isFalse();
        assertThat(message.getBitfield().hasPiece(798)).isFalse();
        assertThat(message.getBitfield().hasPiece(799)).isTrue();
        assertThat(message.getBitfield().hasPiece(900)).isTrue();
        assertThat(message.getBitfield().hasPiece(999)).isTrue();
    }

    @Test
    @DisplayName("should write bitfield message")
    void writeBitfieldMessageTest() {
        Bitfield bitfield = Bitfield.fromBytes(new byte[] {-1, 0, 1});

        var message = new BitfieldMessage(bitfield);

        byte[] bytes = message.toBuffer().getBytes();

        assertThat(bytes).containsExactly(0, 0, 0, 4, 5, -1, 0, 1);
    }

    @Test
    @DisplayName("should parse request message")
    void requestMessageTest() {
        var buffer = ByteBuffer.wrap(new byte[] {0, 0, 0, 2, 0, 0, 0, 4, 0, 0, 0, 3});

        var message = RequestMessage.fromBuffer(buffer);

        assertThat(message.getPieceIndex()).isEqualTo(2);
        assertThat(message.getBegin()).isEqualTo(4);
        assertThat(message.getLength()).isEqualTo(3);
    }

    @Test
    @DisplayName("should write piece message")
    void writeRequestMessageTest() {
        var message = new RequestMessage(2, 4, 3);

        byte[] bytes = message.toBuffer().getBytes();

        assertThat(bytes).containsExactly(0, 0, 0, 13, 6, 0, 0, 0, 2, 0, 0, 0, 4, 0, 0, 0, 3);
    }

    @Test
    @DisplayName("should parse piece message")
    void pieceMessageTest() {
        var buffer = ByteBuffer.wrap(new byte[] {0, 0, 0, 2, 0, 0, 0, 4, -1, 0, 1});

        var message = PieceMessage.fromBuffer(buffer);

        assertThat(message.getPieceIndex()).isEqualTo(2);
        assertThat(message.getBegin()).isEqualTo(4);
        assertThat(message.getData().getBytes()).containsExactly(-1, 0, 1);
    }

    @Test
    @DisplayName("should write piece message")
    void writePieceMessageTest() {
        var message = new PieceMessage(2, 4, Buffer.buffer(new byte[] {-1, 0, 1}));

        byte[] bytes = message.toBuffer().getBytes();

        assertThat(bytes).containsExactly(0, 0, 0, 12, 7, 0, 0, 0, 2, 0, 0, 0, 4, -1, 0, 1);
    }

    @Test
    @DisplayName("should write choke message")
    void writeChokeMessageTest() {
        var message = new ChokeMessage();

        byte[] bytes = message.toBuffer().getBytes();

        assertThat(bytes).containsExactly(0, 0, 0, 1, 0);
    }

    @Test
    @DisplayName("should write unchoke message")
    void writeUnchokeMessageTest() {
        var message = new UnchokeMessage();

        byte[] bytes = message.toBuffer().getBytes();

        assertThat(bytes).containsExactly(0, 0, 0, 1, 1);
    }

    @Test
    @DisplayName("should write interested message")
    void writeInterestedMessageTest() {
        var message = new InterestedMessage();

        byte[] bytes = message.toBuffer().getBytes();

        assertThat(bytes).containsExactly(0, 0, 0, 1, 2);
    }

    @Test
    @DisplayName("should write not_interested message")
    void writeNotInterestedMessageTest() {
        var message = new NotInterestedMessage();

        byte[] bytes = message.toBuffer().getBytes();

        assertThat(bytes).containsExactly(0, 0, 0, 1, 3);
    }

    private ByteBuffer fromBase64(String base64) {
        byte[] handshakeBytes = Base64.getDecoder().decode(base64);
        return ByteBuffer.wrap(handshakeBytes);
    }
}
