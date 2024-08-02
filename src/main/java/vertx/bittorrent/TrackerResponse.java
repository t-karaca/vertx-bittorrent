package vertx.bittorrent;

import be.adaxisoft.bencode.BDecoder;
import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.InvalidBEncodingException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@ToString
public class TrackerResponse {

    private final List<Peer> peers = new ArrayList<>();
    private final int interval;

    public TrackerResponse(BEncodedValue value) throws InvalidBEncodingException {
        BEncodedDict dict = new BEncodedDict(value);

        dict.findValue("peers").map(TrackerResponse::parsePeers4).ifPresent(peers::addAll);
        dict.findBytes("peers6").map(TrackerResponse::parsePeers6).ifPresent(peers::addAll);
        interval = dict.requireInt("interval");

        log.info("Peers:         {}", peers);
        log.info("Interval:      {}", interval);
    }

    public static TrackerResponse fromBuffer(Buffer buffer) {
        try (var is = new ByteArrayInputStream(buffer.getBytes())) {
            return fromInputStream(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrackerResponse fromInputStream(InputStream inputStream) {
        try {
            BEncodedValue value = BDecoder.decode(inputStream);
            return new TrackerResponse(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Collection<Peer> parsePeers4(Object value) {
        if (value instanceof byte[] bytes) {

            return parsePeersFromBytes(bytes, 4);

        } else if (value instanceof List) {

            @SuppressWarnings("unchecked")
            List<BEncodedValue> peers = (List<BEncodedValue>) value;

            return peers.stream()
                    .map(BEncodedDict::from)
                    .map(Peer::fromDict)
                    .filter(Objects::nonNull)
                    .toList();

        } else {
            throw new IllegalArgumentException("value is not a byte array or a list");
        }
    }

    private static Collection<Peer> parsePeers6(byte[] bytes) {
        return parsePeersFromBytes(bytes, 16);
    }

    private static Collection<Peer> parsePeersFromBytes(byte[] bytes, int addressLength) {
        List<Peer> peers = new ArrayList<>();

        int entryLength = addressLength + 2;

        if (bytes.length % entryLength != 0) {
            throw new IllegalArgumentException("byte array has unexpected length: " + bytes.length);
        }

        int peersCount = bytes.length / entryLength;

        for (int i = 0; i < peersCount; i++) {

            byte[] addressBytes = Arrays.copyOfRange(bytes, entryLength * i, entryLength * i + addressLength);
            int port = ((bytes[entryLength * i + addressLength] & 0xFF) << 8)
                    | (bytes[entryLength * i + addressLength + 1] & 0xFF);

            try {
                InetAddress addr = InetAddress.getByAddress(addressBytes);
                SocketAddress socketAddress = SocketAddress.inetSocketAddress(new InetSocketAddress(addr, port));

                Peer peer = new Peer(socketAddress, null);
                if (port >= 0) peers.add(peer);

            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        return peers;
    }
}
