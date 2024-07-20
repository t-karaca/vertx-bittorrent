package vertx.bittorrent;

import be.adaxisoft.bencode.BDecoder;
import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.BEncoder;
import io.vertx.core.buffer.Buffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@ToString
public class Torrent {

    private final String announce;

    private final byte[] infoHash;
    private final String name;
    private final long length;
    private final long pieceLength;

    private final String comment;
    private final String createdBy;
    private final Instant creationDate;

    public Torrent(BEncodedValue value) throws IOException, NoSuchAlgorithmException {
        BEncodedDict dict = new BEncodedDict(value);

        announce = dict.requireString("announce");

        comment = dict.findString("comment").orElse(null);
        createdBy = dict.findString("created by").orElse(null);
        creationDate =
                dict.findLong("creation date").map(Instant::ofEpochSecond).orElse(null);

        BEncodedDict info = dict.requireDict("info");

        length = info.requireLong("length");
        pieceLength = info.requireLong("piece length");
        name = info.findString("name").orElse(null);

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        ByteBuffer buffer = BEncoder.encode(info.getMap());
        infoHash = digest.digest(buffer.array());

        log.debug("Announce:      {}", announce);
        log.debug("Length:        {}", length);
        log.debug("Piece Length:  {}", pieceLength);
        log.debug("Name:          {}", name);
        log.debug("Info hash:     {}", getHexEncodedInfoHash());
    }

    public String getHexEncodedInfoHash() {
        return HexFormat.of().formatHex(infoHash);
    }

    public static Torrent fromBuffer(Buffer buffer) {
        try (var is = new ByteArrayInputStream(buffer.getBytes())) {
            return fromInputStream(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Torrent fromInputStream(InputStream inputStream) {
        try {
            BEncodedValue value = BDecoder.decode(inputStream);
            return new Torrent(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
