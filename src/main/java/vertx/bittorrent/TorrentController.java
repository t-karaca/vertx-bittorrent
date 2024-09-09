package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TorrentController {

    private final Vertx vertx;

    private final ClientOptions clientOptions;

    private TorrentState torrentState;

    private final List<PeerConnection> connections = new ArrayList<>();
    private final Set<Integer> processingPieces = new HashSet<>();
    private final Set<Peer> connectingPeers = new HashSet<>();

    private ClientState clientState;
    private Tracker tracker;
    private NetClient netClient;
    private NetServer netServer;

    private int maxConnections = 50;
    private int maxLeechingPeers = 3;
    private int maxOptimisticLeechingPeers = 1;

    private SecureRandom random = new SecureRandom();

    private long timerId = -1;
    private long unchokeTimerId = -1;
    private long optimisticUnchokeTimerId = -1;

    private long connectTimerId = -1;

    private List<Peer> connectionQueue = new ArrayList<>();

    public TorrentController(Vertx vertx, ClientState clientState, ClientOptions clientOptions) {
        this.vertx = vertx;
        this.clientState = clientState;
        this.clientOptions = clientOptions;
    }

    private boolean isFreeRider() {
        return Boolean.parseBoolean(System.getenv("VB_IS_FREE_RIDER"));
    }

    public void start(Torrent torrent) {
        // clientState = new ClientState(vertx, torrent, clientOptions.getDataDir());
        torrentState = new TorrentState(vertx, torrent, clientOptions.getDataDir());

        tracker = new Tracker(vertx, clientState, torrentState);

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

            PeerConnection connection = new PeerConnection(socket, clientState, torrentState, peer);

            setupPeerConnection(connection);
        });

        torrentState
                .checkPiecesOnDisk()
                .flatMap(v -> netServer.listen(clientOptions.getServerPort()))
                .onSuccess(server -> {
                    log.info("Listening on port {}", server.actualPort());
                    torrentState.setServerPort(server.actualPort());

                    tracker.announce();
                });

        timerId = vertx.setPeriodic(1_000, id -> {
            double totalDownloadRate = 0.0;
            double totalUploadRate = 0.0;

            for (var connection : connections) {
                int deltaBytes = connection.getBytesDownloaded() - connection.getPreviousBytesDownloaded();
                int deltaBytesUploaded = connection.getBytesUploaded() - connection.getPreviousBytesUploaded();

                clientState.addTotalBytesDownloaded(deltaBytes);
                clientState.addTotalBytesUploaded(deltaBytesUploaded);

                totalDownloadRate += deltaBytes;
                totalUploadRate += deltaBytesUploaded;

                connection.setPreviousBytesDownloaded(connection.getBytesDownloaded());
                connection.setPreviousBytesUploaded(connection.getBytesUploaded());
            }

            long completedBytes = torrentState.getCompletedBytes();
            double downloadedRatio =
                    completedBytes / (double) torrentState.getTorrent().getLength();

            String progress = String.format("%.02f", downloadedRatio * 100.0);

            long seeding = getSeedingPeersCount();
            long leeching = getLeechingPeersCount();
            log.info(
                    "[{}] {}% ({} / {}) (↓ {}/s | ↑ {}/s) ({} connected peers, {} seeding, {} leeching) ({}"
                            + " downloaded, {} uploaded)",
                    torrentState.getTorrent().getName(),
                    progress,
                    ByteFormat.format(completedBytes),
                    ByteFormat.format(torrentState.getTorrent().getLength()),
                    ByteFormat.format(totalDownloadRate),
                    ByteFormat.format(totalUploadRate),
                    connections.size(),
                    seeding,
                    leeching,
                    ByteFormat.format(clientState.getTotalBytesDownloaded()),
                    ByteFormat.format(clientState.getTotalBytesUploaded()));
        });

        unchokeTimerId = vertx.setPeriodic(10_000, id -> {
            if (isFreeRider()) {
                return;
            }

            log.debug("Starting unchoke cycle");

            var unchokedPeers = connections.stream()
                    .filter(connection -> !connection.isChoked())
                    .peek(connection -> log.info(
                            "[{}] Unchoked peer found with downloadRate: {}/s",
                            connection.getPeer().getAddress().port(),
                            ByteFormat.format(connection.getAverageDownloadRate())))
                    .toList();

            var peersToUnchoke = connections.stream()
                    .filter(PeerConnection::isRemoteInterested)
                    .sorted(Comparator.comparingDouble(PeerConnection::getAverageDownloadRate)
                            .reversed())
                    .limit(maxLeechingPeers)
                    .peek(PeerConnection::unchoke)
                    .peek(connection -> log.info(
                            "[{}] Unchoking peer with downloadRate: {}/s",
                            connection.getPeer().getAddress().port(),
                            ByteFormat.format(connection.getAverageDownloadRate())))
                    .toList();

            for (var peer : unchokedPeers) {
                if (!peersToUnchoke.contains(peer)) {
                    peer.choke();
                    log.info(
                            "[{}] Choking peer with downloadRate: {}/s",
                            peer.getPeer().getAddress().port(),
                            ByteFormat.format(peer.getAverageDownloadRate()));
                }
            }

            log.debug("End unchoke cycle");
        });

        optimisticUnchokeTimerId = vertx.setPeriodic(30_000, id -> {
            if (isFreeRider()) {
                return;
            }

            var chokedPeers = connections.stream()
                    .filter(connection -> connection.isRemoteInterested() && connection.isChoked())
                    .toList();

            if (!chokedPeers.isEmpty()) {
                var connection = chokedPeers.get(random.nextInt(chokedPeers.size()));

                log.info(
                        "[{}] Optimistic unchoking peer",
                        connection.getPeer().getAddress().port());
                connection.unchoke();
            }
        });
    }

    public Future<Void> close() {
        log.info("Shutting down TorrentController");

        vertx.cancelTimer(timerId);
        vertx.cancelTimer(unchokeTimerId);
        vertx.cancelTimer(optimisticUnchokeTimerId);

        return Future.join(netServer.close(), netClient.close(), torrentState.close(), tracker.close())
                .mapEmpty();
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
        return !torrentState.getBitfield().hasPiece(pieceIndex)
                && connection.getBitfield().hasPiece(pieceIndex)
                && !isPieceRequested(pieceIndex)
                && !isProcessingPiece(pieceIndex);
    }

    private boolean hasRequiredPieces(PeerConnection connection) {
        for (int i = 0; i < torrentState.getTorrent().getPiecesCount(); i++) {
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

        for (int i = 0; i < torrentState.getTorrent().getPiecesCount(); i++) {
            if (hasRequiredPiece(connection, i)) {
                if (pieceIndex == -1) {
                    pieceIndex = i;
                } else if (random.nextInt((int) torrentState.getTorrent().getPiecesCount()) == 0) {
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

    private void unchokeNext() {
        connections.stream()
                .filter(conn -> conn.isChoked() && conn.isRemoteInterested())
                .sorted((a, b) -> (int) (a.getCurrentRemoteWaitingDuration() - b.getCurrentRemoteWaitingDuration()))
                .findFirst()
                .ifPresent(PeerConnection::unchoke);
    }

    private Future<PeerConnection> connectToPeer(Peer peer) {
        for (var connection : connections) {
            if (peer.equals(connection.getPeer())) {
                return Future.succeededFuture(connection);
            }
        }

        return PeerConnection.connect(netClient, clientState, torrentState, peer)
                .onSuccess(connection -> setupPeerConnection(connection));
    }

    private void setupPeerConnection(PeerConnection connection) {

        connections.add(connection);

        PeerStats peerStats = getStatsForPeer(connection.getPeer());
        PeerStats.TorrentStats stats = peerStats.statsForTorrent(torrentState.getTorrent());
        stats.setConnected(true);

        if (peerStats.isFreeRider()) {
            log.info(
                    "Closing connection to {} because free rider",
                    connection.getPeer().getAddress().hostAddress());
            connection.close();
        }

        connection.onHandshake(handshake -> {
            if (Arrays.equals(handshake.getPeerId(), clientState.getPeerId())) {
                // we connected to ourselves
                connection.close();
            }

            // clientState
            //         .getTorrentByInfoHash(handshake.getInfoHash())
            //         .ifPresentOrElse(connection::assignTorrent, connection::close);

            if (!HashUtils.isEqual(
                    handshake.getInfoHash(), torrentState.getTorrent().getInfoHash())) {
                // other peer requested unknown info hash (e.g. other torrent)
                connection.close();
            } else if (Arrays.equals(handshake.getPeerId(), clientState.getPeerId())) {
                // we connected to ourselves
                connection.close();
            } else {
                connection.handshake();

                if (!isFreeRider() && torrentState.getBitfield().hasAnyPieces()) {
                    connection.bitfield();
                }
            }
        });

        connection.onBitfield(bitfield -> {
            if (torrentState.isTorrentComplete()
                    && bitfield.cardinality() == torrentState.getTorrent().getPiecesCount()) {
                // we have all pieces and they have all pieces
                // no reason to keep the connection open
                connection.close();
            }

            if (!connection.isInterested() && hasRequiredPieces(connection)) {
                connection.interested();
            }
        });

        connection.onInterested(v -> {
            if (!isFreeRider() && getLeechingPeersCount() < maxLeechingPeers) {
                connection.unchoke();
            }
        });

        connection.onNotInterested(v -> {
            connection.choke();

            if (!isFreeRider() && getLeechingPeersCount() < maxLeechingPeers) {
                unchokeNext();
            }
        });

        connection.onRequest(request -> {
            if (!isFreeRider() && !connection.isChoked()) {
                torrentState.readPieceFromDisk(request.getPieceIndex()).onSuccess(buffer -> {
                    connection
                            .piece(
                                    request.getPieceIndex(),
                                    request.getBegin(),
                                    buffer.slice(request.getBegin(), request.getBegin() + request.getLength()))
                            .onSuccess(v -> stats.addUploadedBytes(request.getLength()));
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

                torrentState
                        .writePieceToDisk(piece)
                        .onFailure(ex -> {
                            log.error("Could not write piece to file", ex);
                            processingPieces.remove(piece.getIndex());
                            requestNextPiece(connection);
                        })
                        .onSuccess(v -> {
                            stats.addDownloadedBytes(piece.getData().length());

                            processingPieces.remove(piece.getIndex());

                            torrentState.getBitfield().setPiece(piece.getIndex());

                            for (var conn : connections) {
                                conn.have(piece.getIndex());
                            }

                            if (torrentState.isTorrentComplete()) {
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

        connection.onClosed(v -> {
            stats.setConnected(false);

            connections.remove(connection);

            if (!isFreeRider() && getLeechingPeersCount() < maxLeechingPeers) {
                unchokeNext();
            }
        });
    }

    private PeerStats getStatsForPeer(Peer peer) {
        return clientState.getPeerStats().computeIfAbsent(peer.getAddress().hostAddress(), PeerStats::new);
    }

    public void handleFreeRider(String address) {
        for (PeerConnection connection : connections) {
            if (address.equals(connection.getPeer().getAddress().hostAddress())) {
                log.info("Closing connection to {} because free rider", address);
                connection.close();
            }
        }
    }
}
