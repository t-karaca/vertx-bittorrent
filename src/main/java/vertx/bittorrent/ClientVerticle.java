package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final String torrentFileName;

    private final List<PeerConnection> connections = new ArrayList<>();
    private final Set<Integer> processingPieces = new HashSet<>();
    private final Set<Peer> connectingPeers = new HashSet<>();

    private ClientState clientState;
    private Tracker tracker;
    private NetClient netClient;
    private NetServer netServer;

    private int maxConnections = 50;
    private int maxDownloadingPeers = 6;
    private SecureRandom random = new SecureRandom();

    private long timerId = -1;
    private long connectTimerId = -1;

    private List<Peer> connectionQueue = new ArrayList<>();

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
                if (!isConnectedToPeer(peer) && !connectionQueue.contains(peer)) {
                    connectionQueue.add(peer);
                }
            }

            if (connectTimerId == -1) {
                connectToPeers();

                connectTimerId = vertx.setPeriodic(10_000, id -> {
                    connectToPeers();
                });
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
            double totalDownloadRate = 0.0;
            double totalUploadRate = 0.0;

            double totalAvgDownloadRate = 0.0;
            double totalAvgUploadRate = 0.0;

            for (var connection : connections) {
                int deltaBytes = connection.getBytesDownloaded() - connection.getPreviousBytesDownloaded();
                int deltaBytesUploaded = connection.getBytesUploaded() - connection.getPreviousBytesUploaded();

                clientState.addTotalBytesDownloaded(deltaBytes);
                clientState.addTotalBytesUploaded(deltaBytesUploaded);

                totalDownloadRate += deltaBytes;
                totalUploadRate += deltaBytesUploaded;

                connection.setPreviousBytesDownloaded(connection.getBytesDownloaded());
                connection.setPreviousBytesUploaded(connection.getBytesUploaded());

                totalAvgDownloadRate += connection.getAverageDownloadRate();
                totalAvgUploadRate += connection.getAverageUploadRate();
            }

            long completedBytes = clientState.getCompletedBytes();
            long remainingBytes = clientState.getTorrent().getLength() - completedBytes;
            long remainingTime = (long) (remainingBytes / totalAvgDownloadRate);
            double downloadedRatio =
                    completedBytes / (double) clientState.getTorrent().getLength();

            String progress = String.format("%.02f", downloadedRatio * 100.0);

            long seeding = getSeedingPeersCount();
            long leeching = getLeechingPeersCount();
            long downloading = getDownloadingPeersCount();

            log.info(
                    "{}% ({} / {}) (↓ {}/s | ↑ {}/s) ({} connected peers, {} seeding, {} leeching)",
                    progress,
                    ByteFormat.format(completedBytes),
                    ByteFormat.format(clientState.getTorrent().getLength()),
                    ByteFormat.format(totalDownloadRate),
                    ByteFormat.format(totalUploadRate),
                    connections.size(),
                    seeding,
                    leeching);
        });

        vertx.setPeriodic(10_000, id -> {
            for (var connection : connections) {
                connection.keepAlive();
            }

            int downloadingPeersCount = getDownloadingPeersCount();

            if (downloadingPeersCount < maxDownloadingPeers) {
                return;
            }

            // check if we have enough connected peers to rotate on
            if (connections.size() > downloadingPeersCount) {

                List<PeerConnection> untestedPeers = connections.stream()
                        .filter(conn -> !conn.isDownloading())
                        .filter(conn -> conn.getDownloadingDuration() < 10.0)
                        .filter(this::hasRequiredPieces)
                        .sorted(Comparator.comparingDouble(PeerConnection::getRemoteUnchokedDuration)
                                .thenComparingDouble(PeerConnection::getTotalWaitingDuration))
                        .collect(Collectors.toCollection(ArrayList::new));

                List<PeerConnection> testedPeers = connections.stream()
                        .filter(conn -> !conn.isDownloading())
                        .filter(conn -> conn.getDownloadingDuration() >= 10.0)
                        .filter(this::hasRequiredPieces)
                        .sorted(Comparator.comparingDouble(PeerConnection::getAverageDownloadRate)
                                .reversed())
                        .peek(conn -> log.debug(
                                "[{}] Tested: {}/s, duration: {}",
                                conn.getPeer(),
                                ByteFormat.format(conn.getAverageDownloadRate()),
                                conn.getDownloadingDuration()))
                        .collect(Collectors.toCollection(ArrayList::new));

                List<PeerConnection> seedingPeers = connections.stream()
                        .filter(conn -> conn.isDownloading())
                        .filter(conn -> conn.getDownloadingDuration() >= 10.0)
                        .sorted(Comparator.comparingDouble(PeerConnection::getAverageDownloadRate))
                        .peek(conn -> log.debug(
                                "[{}] Monitoring: {}/s, duration: {}",
                                conn.getPeer(),
                                ByteFormat.format(conn.getAverageDownloadRate()),
                                conn.getDownloadingDuration()))
                        .collect(Collectors.toCollection(ArrayList::new));

                int rotatingPeers = Math.max(0, seedingPeers.size() - maxDownloadingPeers + 2);
                while (rotatingPeers > 2) {
                    var conn = seedingPeers.remove(0);
                    conn.setDownloading(false);
                    rotatingPeers--;
                    log.debug(
                            "[{}] Not interested in peer with download rate: {}/s",
                            conn.getPeer(),
                            ByteFormat.format(conn.getAverageDownloadRate()));
                }

                while (rotatingPeers > 0 && !untestedPeers.isEmpty()) {
                    var conn = untestedPeers.remove(0);
                    var seedingConn = seedingPeers.remove(0);
                    conn.setDownloading(true);
                    seedingConn.setDownloading(false);
                    requestNextPiece(conn);
                    rotatingPeers--;
                    log.debug("[{}] Testing download speed of peer", conn.getPeer());
                }

                while (rotatingPeers > 0 && !testedPeers.isEmpty()) {
                    var seedingConn = seedingPeers.remove(0);
                    var testedConn = testedPeers.remove(0);

                    if (testedConn.getAverageDownloadRate() > seedingConn.getAverageDownloadRate()) {
                        testedConn.setDownloading(true);
                        requestNextPiece(testedConn);
                        seedingConn.setDownloading(false);
                        rotatingPeers--;
                        log.debug(
                                "[{}] Switching to faster peer with download rate: {}/s",
                                testedConn.getPeer(),
                                ByteFormat.format(testedConn.getAverageDownloadRate()));
                    } else {
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Shutting down client");

        vertx.cancelTimer(timerId);

        tracker.close().onComplete(ar -> stopPromise.complete());
    }

    private void connectToPeers() {
        int connectionsToOpen =
                Math.min(maxConnections - connections.size() - connectingPeers.size(), connectionQueue.size());

        if (connectionsToOpen <= 0) {
            return;
        }

        log.info("Trying to open {} connections", connectionsToOpen);

        while (connectionsToOpen > 0 && connectionQueue.size() > 0) {
            int index = random.nextInt(connectionQueue.size());

            Peer peer = connectionQueue.remove(index);

            if (isConnectedToPeer(peer)) {
                continue;
            }

            connectingPeers.add(peer);

            connectionsToOpen--;
            connectToPeer(peer).onComplete(ar -> connectingPeers.remove(peer));
        }
    }

    private int getSeedingPeersCount() {
        return (int) connections.stream()
                .filter(conn -> !conn.isRemoteChoked() && conn.isInterested())
                .count();
    }

    private int getDownloadingPeersCount() {
        return (int) connections.stream()
                .filter(conn -> conn.isDownloading() && !conn.isRemoteChoked())
                .count();
    }

    private int getLeechingPeersCount() {
        return (int) connections.stream().filter(conn -> !conn.isChoked()).count();
    }

    private boolean isConnectedToPeer(Peer peer) {
        for (var connection : connections) {
            if (connection.getPeer().equals(peer)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasRequiredPiece(PeerConnection connection, int pieceIndex) {
        return !clientState.getBitfield().hasPiece(pieceIndex)
                && connection.getBitfield().hasPiece(pieceIndex)
                && !isPieceRequested(pieceIndex)
                && !isProcessingPiece(pieceIndex);
    }

    private boolean hasRequiredPieces(PeerConnection connection) {
        for (int i = 0; i < clientState.getTorrent().getPiecesCount(); i++) {
            if (hasRequiredPiece(connection, i)) {
                return true;
            }
        }

        return false;
    }

    private int requestNextPiece(PeerConnection connection) {
        if (!connection.isInterested() || connection.isRemoteChoked()) {
            return -1;
        }

        int pieceIndex = -1;

        for (int i = 0; i < clientState.getTorrent().getPiecesCount(); i++) {
            if (hasRequiredPiece(connection, i)) {
                if (pieceIndex == -1) {
                    pieceIndex = i;
                } else if (random.nextInt((int) clientState.getTorrent().getPiecesCount()) == 0) {
                    pieceIndex = i;
                }
            }
        }

        if (pieceIndex != -1) {
            log.debug("Requesting piece {} from peer {}", pieceIndex, connection.getPeer());
            connection.requestPiece(pieceIndex);
        }

        return pieceIndex;
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
                // other peer requested unknown info hash (e.g. other torrent)
                connection.close();
            } else if (Arrays.equals(handshake.getPeerId(), clientState.getPeerId())) {
                // we connected to ourselves
                connection.close();
            } else {
                connection.handshake();

                if (clientState.getBitfield().hasAnyPieces()) {
                    connection.bitfield();
                }
            }
        });

        connection.onBitfield(bitfield -> {
            if (clientState.isTorrentComplete()
                    && bitfield.cardinality() == clientState.getTorrent().getPiecesCount()) {
                // we have all pieces and they have all pieces
                // no reason to keep the connection open
                connection.close();
            }

            if (!connection.isInterested() && hasRequiredPieces(connection)) {
                connection.interested();
            }
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
            if (!connection.isInterested() && hasRequiredPiece(connection, i)) {
                connection.interested();
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

        connection.onChoked(v -> {
            connection.setDownloading(false);
        });

        connection.onUnchoked(v -> {
            if (getDownloadingPeersCount() < maxDownloadingPeers) {
                connection.setDownloading(true);
                requestNextPiece(connection);
            }
        });

        connection.onClosed(v -> connections.remove(connection));
    }
}
