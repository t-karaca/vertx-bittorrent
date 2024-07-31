package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class ClientState {
    private final Vertx vertx;

    @Getter
    private final Torrent torrent;

    @Getter
    private final Bitfield bitfield;

    @Getter
    private final byte[] peerId = generatePeerId();

    private final Map<String, AsyncFile> fileMap = new HashMap<>();

    public ClientState(Vertx vertx, Torrent torrent) {
        this.vertx = vertx;
        this.torrent = torrent;
        this.bitfield = Bitfield.fromSize((int) torrent.getPiecesCount());

        FileSystem fs = vertx.fileSystem();

        for (var file : torrent.getFiles()) {
            Path parent = Path.of(file.getPath()).getParent();
            if (parent != null) {
                fs.mkdirsBlocking(parent.toString());
            }

            AsyncFile asyncFile = fs.openBlocking(
                    file.getPath(), new OpenOptions().setRead(true).setWrite(true));

            fileMap.put(file.getPath(), asyncFile);
        }
    }

    public boolean isTorrentComplete() {
        return bitfield.cardinality() == torrent.getPiecesCount();
    }

    public Future<Void> close() {
        return Future.all(fileMap.values().stream().map(AsyncFile::close).toList())
                .mapEmpty();
    }

    public Future<Void> checkPiecesOnDisk() {
        List<Future<Buffer>> futures = new ArrayList<>((int) torrent.getPiecesCount());

        for (int i = 0; i < torrent.getPiecesCount(); i++) {
            int pieceIndex = i;

            Future<Buffer> future = readPieceFromDisk(pieceIndex).onSuccess(buffer -> {
                byte[] pieceHash = HashUtils.sha1(buffer);

                if (HashUtils.isEqual(torrent.getHashForPiece(pieceIndex), pieceHash)) {
                    bitfield.setPiece(pieceIndex);
                }
            });

            futures.add(future);
        }

        return Future.all(futures).mapEmpty();
    }

    public Future<Buffer> readPieceFromDisk(int index) {
        int pieceOffset = 0;
        int pieceLength = (int) torrent.getLengthForPiece(index);

        Buffer buffer = Buffer.buffer(pieceLength);

        List<Future<Buffer>> futures = new ArrayList<>();

        while (pieceOffset < pieceLength) {
            FilePosition position = torrent.getFilePositionForPiece(index, pieceOffset);
            FileInfo fileInfo = position.getFileInfo();

            int bytesToRead = (int) Math.min(fileInfo.getLength() - position.getOffset(), pieceLength - pieceOffset);

            AsyncFile file = fileMap.get(fileInfo.getPath());

            futures.add(file.read(buffer, pieceOffset, position.getOffset(), bytesToRead));

            pieceOffset += bytesToRead;
        }

        return Future.all(futures).map(buffer);
    }

    public Future<Void> writePieceToDisk(Piece piece) {
        int pieceOffset = 0;
        int pieceLength = piece.getData().length();

        List<Future<Void>> futures = new ArrayList<>();

        while (pieceOffset < pieceLength) {
            FilePosition position = torrent.getFilePositionForPiece(piece.getIndex(), pieceOffset);
            FileInfo fileInfo = position.getFileInfo();

            int bytesToWrite = (int) Math.min(fileInfo.getLength() - position.getOffset(), pieceLength - pieceOffset);

            Buffer slice = piece.getData().slice(pieceOffset, pieceOffset + bytesToWrite);

            AsyncFile file = fileMap.get(fileInfo.getPath());

            futures.add(file.write(slice, position.getOffset()));

            pieceOffset += bytesToWrite;
        }

        return Future.all(futures).mapEmpty();
    }

    private static byte[] generatePeerId() {
        byte[] prefixBytes = "-VB1000-".getBytes(StandardCharsets.UTF_8);

        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);

        return ArrayUtils.addAll(prefixBytes, bytes);
    }
}
