package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.BEncoder;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.Torrent;
import vertx.bittorrent.TrackerResponse;

@WireMockTest(httpPort = 9876)
public class TorrentTest {
    @Test
    void test() {
        var vertx = Vertx.vertx();
        Buffer buffer = vertx.fileSystem().readFileBlocking("example.torrent");
        Torrent torrent = Torrent.fromBuffer(buffer);

        assertEquals("http://localhost:6969/announce", torrent.getAnnounce());
        assertEquals("example.txt", torrent.getName());
    }

    @Test
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
