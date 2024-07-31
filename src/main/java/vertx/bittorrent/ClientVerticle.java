package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.net.NetClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final String torrentFileName;

    private final List<PeerConnection> connections = new ArrayList<>();
    private final Set<Integer> processingPieces = new HashSet<>();

    private ClientState clientState;
    private Tracker tracker;
    private NetClient netClient;

    private long timerId;

    @Override
    public void start() throws Exception {
        FileSystem fs = vertx.fileSystem();

        if (!fs.existsBlocking(torrentFileName)) {
            log.error("Could not find torrent file at: {}", torrentFileName);
            vertx.close();
            return;
        }

        Buffer torrentBuffer = fs.readFileBlocking(torrentFileName);

        Torrent torrent = Torrent.fromBuffer(torrentBuffer);

        clientState = new ClientState(vertx, torrent);
        tracker = new Tracker(vertx, clientState);

        tracker.onPeersReceived(peers -> {
            for (Peer peer : peers) {
                connectToPeer(peer);
            }
        });

        netClient = vertx.createNetClient();

        clientState.checkPiecesOnDisk().onSuccess(v -> {
            tracker.announce();
        });

        timerId = vertx.setPeriodic(1_000, id -> {
            double totalDownloadRate = 0.0f;

            for (var connection : connections) {
                int deltaBytes = connection.getBytesDownloaded() - connection.getPreviousBytesDownloaded();

                clientState.addTotalBytesDownloaded(deltaBytes);

                totalDownloadRate += deltaBytes;

                connection.setDownloadRate(deltaBytes);

                connection.setPreviousBytesDownloaded(connection.getBytesDownloaded());
            }

            long completedBytes = clientState.getCompletedBytes();
            double downloadedRatio =
                    completedBytes / (double) clientState.getTorrent().getLength();

            String progress = String.format("%.02f", downloadedRatio * 100.0);

            log.info(
                    "Download progress: {}% ({} / {}) ({}/s)",
                    progress,
                    ByteFormat.format(completedBytes),
                    ByteFormat.format(clientState.getTorrent().getLength()),
                    ByteFormat.format(totalDownloadRate));
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Shutting down client");

        vertx.cancelTimer(timerId);

        tracker.close().onComplete(ar -> stopPromise.complete());
    }

    private void requestNextPiece(PeerConnection connection) {
        for (int i = 0; i < clientState.getTorrent().getPiecesCount(); i++) {
            if (!clientState.getBitfield().hasPiece(i)
                    && connection.getBitfield().hasPiece(i)
                    && !isProcessingPiece(i)
                    && !isPieceRequested(i)) {

                log.debug("Requesting piece {} from peer {}", i, connection.getPeer());
                connection.requestPiece(i);
                break;
            }
        }
    }

    private boolean isProcessingPiece(int pieceIndex) {
        return processingPieces.contains(pieceIndex);
    }

    private boolean isPieceRequested(int pieceIndex) {
        for (var connection : connections) {
            if (connection.isPieceRequested(pieceIndex)) {
                return true;
            }
        }

        return false;
    }

    private Future<PeerConnection> connectToPeer(Peer peer) {
        for (var connection : connections) {
            if (peer.equals(connection.getPeer())) {
                return Future.succeededFuture(connection);
            }
        }

        return PeerConnection.connect(netClient, clientState, peer).onSuccess(connection -> {
            connections.add(connection);

            connection.onBitfield(bitfield -> {
                connection.interested();
            });

            connection.onPieceCompleted(piece -> {
                if (piece.isHashValid()) {
                    processingPieces.add(piece.getIndex());

                    clientState
                            .writePieceToDisk(piece)
                            .onFailure(ex -> {
                                log.error("Could not write piece to file", ex);
                                processingPieces.remove(piece.getIndex());
                                requestNextPiece(connection);
                            })
                            .onSuccess(v -> {
                                processingPieces.remove(piece.getIndex());

                                clientState.getBitfield().setPiece(piece.getIndex());

                                if (clientState.isTorrentComplete()) {
                                    log.info("Download completed");

                                    vertx.cancelTimer(timerId);
                                    tracker.completed();
                                } else {
                                    requestNextPiece(connection);
                                }
                            });
                } else {
                    // peer sent faulty piece
                    log.warn("Received invalid piece for index: {}", piece.getIndex());
                    requestNextPiece(connection);
                }
            });

            connection.onUnchoked(v -> {
                requestNextPiece(connection);
            });

            connection.onClosed(v -> connections.remove(connection));
        });
    }
}
