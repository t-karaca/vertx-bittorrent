package vertx.bittorrent;

import be.adaxisoft.bencode.BDecoder;
import be.adaxisoft.bencode.BEncodedValue;
import io.vertx.core.buffer.Buffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@ToString
public class Torrent {

    public static final int DIGEST_LENGTH = 20;

    private final String announce;

    private final byte[] infoHash;
    private final String name;
    private final long length;
    private final long pieceLength;
    private final long piecesCount;
    private final byte[] pieces;

    private final List<FileInfo> files = new ArrayList<>();

    private final String comment;
    private final String createdBy;
    private final Instant creationDate;

    public Torrent(BEncodedValue value) throws IOException {
        BEncodedDict dict = new BEncodedDict(value);

        announce = dict.requireString("announce");

        comment = dict.findString("comment").orElse(null);
        createdBy = dict.findString("created by").orElse(null);
        creationDate =
                dict.findLong("creation date").map(Instant::ofEpochSecond).orElse(null);

        BEncodedDict info = dict.requireDict("info");

        name = info.requireString("name");
        pieceLength = info.requireLong("piece length");
        pieces = info.requireBytes("pieces");

        if (pieces.length % DIGEST_LENGTH != 0) {
            log.warn("Field 'pieces' has an invalid count of bytes: {}", pieces.length);
        }

        info.findList("files").ifPresent(list -> list.stream()
                .map(BEncodedDict::from)
                .map(d -> FileInfo.fromDict(name, d))
                .forEach(files::add));

        if (files.isEmpty()) {
            length = info.requireLong("length");

            files.add(new FileInfo(name, length));
        } else {
            length = files.stream().reduce(0L, (total, fileInfo) -> total + fileInfo.getLength(), (a, b) -> a + b);
        }

        infoHash = HashUtils.sha1(info.encode());

        piecesCount = (int) ((length + pieceLength - 1) / pieceLength);

        log.info("Name:          {}", name);
        log.info("Announce:      {}", announce);
        log.info("Length:        {}", length);
        log.info("Piece Length:  {}", pieceLength);
        log.info("Pieces count:  {}", piecesCount);
        log.info("Info hash:     {}", getHexEncodedInfoHash());
        log.info("Files:         {}", files);
    }

    public boolean isSingleFile() {
        return files.size() == 1;
    }

    public boolean isMultiFile() {
        return files.size() > 1;
    }

    public String getHexEncodedInfoHash() {
        return HexFormat.of().formatHex(infoHash);
    }

    public long getLastPieceLength() {
        // last piece can be smaller than piece length
        return length - (piecesCount - 1) * pieceLength;
    }

    public long getLengthForPiece(int index) {
        if (index >= piecesCount || index < 0) {
            throw new IndexOutOfBoundsException(index);
        }

        if (index == piecesCount - 1) {
            return getLastPieceLength();
        }

        return pieceLength;
    }

    public ByteBuffer getHashForPiece(int index) {
        if (index >= piecesCount || index < 0) {
            throw new IndexOutOfBoundsException(index);
        }

        return ByteBuffer.wrap(pieces, index * DIGEST_LENGTH, DIGEST_LENGTH);
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
