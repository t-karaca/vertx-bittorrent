package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
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
@Getter
public class ClientState {
    private final Vertx vertx;

    private Torrent torrent;
    private Bitfield bitfield;

    private final Map<String, AsyncFile> fileMap = new HashMap<>();

    private final byte[] peerId = generatePeerId();

    public ClientState(Vertx vertx) {
        this.vertx = vertx;
    }

    public ClientState setTorrent(Torrent torrent) {
        this.torrent = torrent;
        this.bitfield = Bitfield.fromSize((int) torrent.getPiecesCount());

        for (var file : torrent.getFiles()) {
            Path parent = Path.of(file.getPath()).getParent();
            if (parent != null) {
                vertx.fileSystem().mkdirsBlocking(parent.toString());
            }

            AsyncFile asyncFile = vertx.fileSystem()
                    .openBlocking(
                            file.getPath(), new OpenOptions().setRead(true).setWrite(true));

            fileMap.put(file.getPath(), asyncFile);
        }

        return this;
    }

    public Future<Void> writePieceToDisk(Piece piece) {
        int pieceOffset = 0;
        int pieceLength = piece.getData().length();

        List<Future<Void>> futures = new ArrayList<>();

        while (pieceOffset < pieceLength) {
            FilePosition position = torrent.getFilePositionForBlock(piece.getIndex(), pieceOffset);
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
