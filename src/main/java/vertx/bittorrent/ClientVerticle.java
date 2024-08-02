package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
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
    private NetServer netServer;

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
        netServer = vertx.createNetServer();

        netServer.connectHandler(socket -> {
            Peer peer = new Peer(socket.remoteAddress());
            log.debug("[{}] Peer connected", peer);

            PeerConnection connection = new PeerConnection(socket, clientState, peer);

            setupPeerConnection(connection);
        });

        clientState.checkPiecesOnDisk().flatMap(v -> netServer.listen(12345)).onSuccess(server -> {
            log.info("Listening on port {}", server.actualPort());
            clientState.setServerPort(server.actualPort());

            tracker.announce();
        });

        timerId = vertx.setPeriodic(1_000, id -> {
            double totalDownloadRate = 0.0f;
            double totalUploadRate = 0.0f;

            for (var connection : connections) {
                int deltaBytes = connection.getBytesDownloaded() - connection.getPreviousBytesDownloaded();
                int deltaBytesUploaded = connection.getBytesUploaded() - connection.getPreviousBytesUploaded();

                clientState.addTotalBytesDownloaded(deltaBytes);
                clientState.addTotalBytesUploaded(deltaBytesUploaded);

                totalDownloadRate += deltaBytes;
                totalUploadRate += deltaBytesUploaded;

                connection.setDownloadRate(deltaBytes);

                connection.setPreviousBytesDownloaded(connection.getBytesDownloaded());
                connection.setPreviousBytesUploaded(connection.getBytesUploaded());
            }

            long completedBytes = clientState.getCompletedBytes();
            double downloadedRatio =
                    completedBytes / (double) clientState.getTorrent().getLength();

            String progress = String.format("%.02f", downloadedRatio * 100.0);

            log.info(
                    "{}% ({} / {}) (↓ {}/s | ↑ {}/s)",
                    progress,
                    ByteFormat.format(completedBytes),
                    ByteFormat.format(clientState.getTorrent().getLength()),
                    ByteFormat.format(totalDownloadRate),
                    ByteFormat.format(totalUploadRate));
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

        return PeerConnection.connect(netClient, clientState, peer)
                .onSuccess(connection -> setupPeerConnection(connection));
    }

    private void setupPeerConnection(PeerConnection connection) {
        connections.add(connection);

        connection.onHandshake(handshake -> {
            if (!HashUtils.isEqual(
                    handshake.getInfoHash(), clientState.getTorrent().getInfoHash())) {
                connection.close();
            } else {
                connection.handshake();
                connection.bitfield();
            }
        });

        connection.onBitfield(bitfield -> {
            connection.interested();
        });

        connection.onInterested(v -> {
            connection.unchoke();
        });

        connection.onNotInterested(v -> {
            connection.choke();
        });

        connection.onRequest(request -> {
            if (!connection.isChoked()) {
                clientState.readPieceFromDisk(request.getPieceIndex()).onSuccess(buffer -> {
                    connection.piece(
                            request.getPieceIndex(),
                            request.getBegin(),
                            buffer.slice(request.getBegin(), request.getBegin() + request.getLength()));
                });
            }
        });

        connection.onHasPiece(i -> {
            if (!clientState.getBitfield().hasPiece(i) && !isPieceRequested(i)) {
                if (connection.isRemoteChoked()) {
                    connection.interested();
                } else {
                    requestNextPiece(connection);
                }
            }
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

                            for (var conn : connections) {
                                conn.have(piece.getIndex());
                            }

                            if (clientState.isTorrentComplete()) {
                                log.info("Download completed");

                                tracker.completed();

                                for (var conn : connections) {
                                    conn.notInterested();
                                }

                                // vertx.cancelTimer(timerId);
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
    }
}
