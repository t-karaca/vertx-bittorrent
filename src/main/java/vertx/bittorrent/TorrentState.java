package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TorrentState {
    private final Map<String, AsyncFile> fileMap = new HashMap<>();

    @Getter
    private final Torrent torrent;

    @Getter
    private final Bitfield bitfield;

    private final String dataDir;

    @Getter
    @Setter
    private int serverPort;

    public TorrentState(Vertx vertx, Torrent torrent, String dataDir) {
        String directory = StringUtils.isBlank(dataDir) ? "." : dataDir;

        this.torrent = torrent;
        this.dataDir = directory;
        this.bitfield = Bitfield.fromSize((int) torrent.getPiecesCount());

        FileSystem fs = vertx.fileSystem();

        for (var file : torrent.getFiles()) {
            Path filePath = Paths.get(directory, file.getPath());
            // Path filePath = Path.of(file.getPath());
            Path parent = filePath.getParent();
            if (parent != null) {
                fs.mkdirsBlocking(parent.toString());
            }

            AsyncFile asyncFile = fs.openBlocking(
                    filePath.toString(), new OpenOptions().setRead(true).setWrite(true));

            fileMap.put(file.getPath(), asyncFile);
        }
    }

    public long getCompletedBytes() {
        long completed = 0;

        for (int i = 0; i < torrent.getPiecesCount(); i++) {
            if (bitfield.hasPiece(i)) {
                completed += torrent.getLengthForPiece(i);
            }
        }

        return completed;
    }

    public long getRemainingBytes() {
        return torrent.getLength() - getCompletedBytes();
    }

    public boolean isTorrentComplete() {
        return bitfield.cardinality() == torrent.getPiecesCount();
    }

    public Future<Void> close() {
        return Future.all(fileMap.values().stream().map(AsyncFile::close).toList())
                .mapEmpty();
    }

    public Future<Void> checkPiecesOnDisk() {
        int numThreads = Runtime.getRuntime().availableProcessors();

        log.debug("Checking completed pieces with {} threads ...", numThreads);

        long start = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Void> completionService = new ExecutorCompletionService<>(executor);

        int piecesPerTask = (int) (torrent.getPiecesCount() / numThreads);
        int remainingPieces = (int) (torrent.getPiecesCount() % numThreads);

        Map<String, FileChannel> files = new ConcurrentHashMap<>();

        for (int task = 0; task < numThreads; task++) {
            int pieceIndex = task * piecesPerTask;
            int numPieces = task == numThreads - 1 ? piecesPerTask + remainingPieces : piecesPerTask;

            completionService.submit(() -> {
                byte[] hashOutput = new byte[20];
                ByteBuffer buffer = ByteBuffer.allocate((int) torrent.getPieceLength());

                for (int i = pieceIndex; i < pieceIndex + numPieces; i++) {
                    int pieceOffset = 0;
                    int pieceLength = (int) torrent.getLengthForPiece(i);

                    buffer.position(0);

                    while (pieceOffset < pieceLength) {
                        FilePosition position = torrent.getFilePositionForPiece(i, pieceOffset);
                        FileInfo fileInfo = position.getFileInfo();

                        int bytesToRead =
                                (int) Math.min(fileInfo.getLength() - position.getOffset(), pieceLength - pieceOffset);

                        buffer.limit(pieceOffset + bytesToRead);

                        var file = files.computeIfAbsent(fileInfo.getPath(), path -> {
                            try {
                                return FileChannel.open(Paths.get(dataDir, path), StandardOpenOption.READ);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                        try {
                            int bytesRead = file.read(buffer, position.getOffset());

                            if (bytesRead == -1) {
                                break;
                            }

                            pieceOffset += bytesRead;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    buffer.position(0);

                    boolean isValid = false;

                    if (pieceOffset == pieceLength) {
                        HashUtils.sha1(buffer, hashOutput);

                        if (HashUtils.isEqual(torrent.getHashForPiece(i), hashOutput)) {
                            isValid = true;
                        }
                    }

                    if (isValid) {
                        log.trace("Piece with index {} is valid", i);

                        synchronized (bitfield) {
                            bitfield.setPiece(i);
                        }
                    } else {
                        log.trace("Piece with index {} is invalid", i);
                    }
                }
                return null;
            });
        }

        int finished = 0;
        while (finished < numThreads) {
            try {
                completionService.take();
                finished++;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        for (var file : files.values()) {
            try {
                file.close();
            } catch (IOException e) {
                log.error("Error", e);
            }
        }

        long duration = System.currentTimeMillis() - start;

        log.debug("Checking pieces took {}", Duration.ofMillis(duration));

        return Future.succeededFuture();
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
}
