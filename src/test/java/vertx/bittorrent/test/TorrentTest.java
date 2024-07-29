package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.*;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.BEncoder;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.Torrent;
import vertx.bittorrent.TrackerResponse;

public class TorrentTest {
    @Test
    @DisplayName("should parse single file torrent")
    void testSingleFileTorrent() {
        var vertx = Vertx.vertx();
        Buffer buffer = vertx.fileSystem().readFileBlocking("src/test/resources/bash-5.2.21.tar.gz.torrent");
        Torrent torrent = Torrent.fromBuffer(buffer);

        assertThat(torrent.getAnnounce()).isEqualTo("http://localhost:6969/announce");
        assertThat(torrent.getName()).isEqualTo("bash-5.2.21.tar.gz");
        assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
        assertThat(torrent.getLength()).isEqualTo(10952391);
        assertThat(torrent.getPieceLength()).isEqualTo(262144);
        assertThat(torrent.getPiecesCount()).isEqualTo(42);
        assertThat(torrent.getHexEncodedInfoHash()).isEqualTo("87cb62630d6c0f32d152817e40bb5c33cc9d9e62");
        assertThat(torrent.isSingleFile()).isTrue();
        assertThat(torrent.isMultiFile()).isFalse();
    }

    @Test
    @DisplayName("should parse multi file torrent")
    void testMultiFileTorrent() {
        var vertx = Vertx.vertx();
        Buffer buffer = vertx.fileSystem().readFileBlocking("src/test/resources/multifile-test.torrent");
        Torrent torrent = Torrent.fromBuffer(buffer);

        assertThat(torrent.getAnnounce()).isEqualTo("http://localhost:6969/announce");
        assertThat(torrent.getName()).isEqualTo("multifile-test");
        assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
        assertThat(torrent.getLength()).isEqualTo(17997612);
        assertThat(torrent.getPieceLength()).isEqualTo(262144);
        assertThat(torrent.getPiecesCount()).isEqualTo(69);
        assertThat(torrent.getHexEncodedInfoHash()).isEqualTo("0100dfc0787571978b5e7691f4415d446d782476");
        assertThat(torrent.isSingleFile()).isFalse();
        assertThat(torrent.isMultiFile()).isTrue();
    }

    @Test
    @DisplayName("should parse tracker response")
    void testTracker() throws Exception {
        var peers4 = createPeers4InBytes(2);

        var receivedResponse = Map.of("peers", peers4, "interval", new BEncodedValue(10));
        ByteBuffer buffer = BEncoder.encode(receivedResponse);

        var response = TrackerResponse.fromBuffer(Buffer.buffer(buffer.array()));

        assertThat(response.getPeers()).hasSize(2);
    }

    BEncodedValue createPeers4InBytes(int numPeers) throws Exception {
        byte[] bytes = new byte[2 * 6];

        byte[] ip1 = InetAddress.getByName("10.0.0.1").getAddress();
        int port1 = 1234;

        byte[] ip2 = InetAddress.getByName("10.0.0.2").getAddress();
        int port2 = 4564;

        System.arraycopy(ip1, 0, bytes, 0, ip1.length);
        // bytes[4] = (byte) 0x04;
        // bytes[5] = (byte) 0xD2;
        bytes[4] = (byte) (port1 >>> 8 & 0xFF);
        bytes[5] = (byte) (port1 & 0xFF);

        System.arraycopy(ip2, 0, bytes, ip1.length + 2, ip2.length);
        bytes[10] = (byte) (port2 >>> 8 & 0xFF);
        bytes[11] = (byte) (port2 & 0xFF);

        return new BEncodedValue(bytes);
    }
}
