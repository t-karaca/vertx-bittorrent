package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.dht.DHTClient;
import vertx.bittorrent.model.ClientOptions;
import vertx.bittorrent.model.Peer;
import vertx.bittorrent.model.Torrent;
import vertx.bittorrent.utils.ByteFormat;
import vertx.bittorrent.utils.HashUtils;
import vertx.bittorrent.utils.RandomUtils;

@Slf4j
public class TorrentController {

    private final Vertx vertx;

    private final ClientOptions clientOptions;
    private final DHTClient dhtClient;

    @Getter
    private TorrentState torrentState;

    private final List<PeerConnection> connections = new ArrayList<>();
    private final Set<Integer> processingPieces = new HashSet<>();
    private final Set<Peer> connectingPeers = new HashSet<>();

    private ClientState clientState;
    private Tracker tracker;
    private NetClient netClient;

    private int maxConnections = 50;
    private int maxLeechingPeers = 3;
    private int maxOptimisticLeechingPeers = 1;

    private SecureRandom random = new SecureRandom();

    private long timerId = -1;
    private long unchokeTimerId = -1;
    private long optimisticUnchokeTimerId = -1;

    private long connectTimerId = -1;

    private boolean enteredEndGame = false;

    private List<Peer> connectionQueue = new ArrayList<>();

    public TorrentController(Vertx vertx, ClientState clientState, ClientOptions clientOptions, DHTClient client) {
        this.vertx = vertx;
        this.clientState = clientState;
        this.clientOptions = clientOptions;
        this.dhtClient = client;
    }

    public void start(Torrent torrent) {
        // torrentState = new TorrentState(vertx, torrent, clientOptions.getDataDir());
        torrentState = new TorrentState(vertx, torrent, ".");

        tracker = new Tracker(vertx, clientState, torrentState);

        tracker.onPeersReceived(peers -> {
            for (Peer peer : peers) {
                if (!isConnectedToPeer(peer) && !connectionQueue.contains(peer)) {
                    connectionQueue.add(peer);
                }
            }

            connectToPeers();
        });

        netClient = vertx.createNetClient(new NetClientOptions().setConnectTimeout(5_000));

        torrentState.checkPiecesOnDisk().onSuccess(server -> {
            tracker.announce();

            vertx.setPeriodic(0, 300_000, id -> {
                if (dhtClient != null) {

                    dhtClient.lookupTorrent(torrentState.getTorrent().getInfoHash(), peers -> {
                        for (Peer peer : peers) {
                            if (!isConnectedToPeer(peer) && !connectionQueue.contains(peer)) {
                                connectionQueue.add(peer);
                            }
                        }

                        connectToPeers();
                    });
                }
            });
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
        vertx.cancelTimer(connectTimerId);

        return Future.join(netClient.close(), torrentState.close(), tracker.close())
                .mapEmpty();
    }

    private void connectToPeers() {
        if (connectTimerId != -1) {
            return;
        }

        connectTimerId = vertx.setPeriodic(0L, 10_000, id -> {
            int connectionsToOpen =
                    Math.min(maxConnections - connections.size() - connectingPeers.size(), connectionQueue.size());

            if (connectionsToOpen <= 0) {
                return;
            }

            log.debug("Trying to open {} connections", connectionsToOpen);

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
        });
    }

    private int getSeedingPeersCount() {
        return (int) connections.stream()
                .filter(conn -> !conn.isRemoteChoked() && conn.isInterested())
                .count();
    }

    private int getLeechingPeersCount() {
        return (int) connections.stream().filter(conn -> !conn.isChoked()).count();
    }

    private int getRequestedPiecesCount() {
        return connections.stream()
                .map(PeerConnection::getRequestedPiecesCount)
                .reduce(Integer::sum)
                .orElse(0);
    }

    private boolean isEndGame() {
        int missingPieces = (int) torrentState.getTorrent().getPiecesCount()
                - torrentState.getBitfield().cardinality();

        return getRequestedPiecesCount() + processingPieces.size() >= missingPieces;
    }

    private boolean isConnectedToPeer(Peer peer) {
        for (var connection : connections) {
            if (connection.getPeer().equals(peer)) {
                return true;
            }
        }

        return false;
    }

    private boolean canRequestPiece(PeerConnection connection, int pieceIndex) {
        return !torrentState.getBitfield().hasPiece(pieceIndex)
                && connection.getBitfield().hasPiece(pieceIndex)
                && !isPieceRequested(pieceIndex)
                && !isProcessingPiece(pieceIndex);
    }

    private boolean hasRequiredPieces(PeerConnection connection) {
        for (int i = 0; i < torrentState.getTorrent().getPiecesCount(); i++) {
            if (canRequestPiece(connection, i)) {
                return true;
            }
        }

        return false;
    }

    private void requestNextPieces(PeerConnection connection) {
        if (!connection.isInterested() || connection.isRemoteChoked()) {
            return;
        }

        int numPieces = connection.getMaxRequestedPieces() - connection.getRequestedPiecesCount();

        for (int i = 0; i < numPieces; i++) {
            IntStream.range(0, (int) torrentState.getTorrent().getPiecesCount())
                    .filter(index -> canRequestPiece(connection, index))
                    .reduce(RandomUtils::reservoirSample)
                    .ifPresent(pieceIndex -> {
                        log.debug("Requesting piece {} from peer {}", pieceIndex, connection.getPeer());
                        connection.requestPiece(pieceIndex);
                    });
        }
    }

    private void enterEndGame() {
        if (enteredEndGame) {
            return;
        }

        enteredEndGame = true;

        log.info("Entering end game");

        IntStream.range(0, (int) torrentState.getTorrent().getPiecesCount())
                .filter(index -> !torrentState.getBitfield().hasPiece(index))
                .forEach(index -> connections.stream()
                        .filter(conn -> conn.getBitfield().hasPiece(index))
                        .sorted(Comparator.comparingDouble(PeerConnection::getAverageDownloadRate)
                                .reversed())
                        .limit(10)
                        .forEach(conn -> conn.requestPiece(index)));
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

        connection.onHandshake(handshake -> {
            if (!HashUtils.isEqual(
                    handshake.getInfoHash(), torrentState.getTorrent().getInfoHash())) {
                // other peer requested unknown info hash (e.g. other torrent)
                connection.close();
            } else if (torrentState.getBitfield().hasAnyPieces()) {
                connection.bitfield();
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
            if (getLeechingPeersCount() < maxLeechingPeers) {
                connection.unchoke();
            }
        });

        connection.onNotInterested(v -> {
            connection.choke();

            if (getLeechingPeersCount() < maxLeechingPeers) {
                unchokeNext();
            }
        });

        connection.onRequest(request -> {
            if (!connection.isChoked()) {
                torrentState.readPieceFromDisk(request.getPieceIndex()).onSuccess(buffer -> {
                    connection.piece(
                            request.getPieceIndex(),
                            request.getBegin(),
                            buffer.slice(request.getBegin(), request.getBegin() + request.getLength()));
                });
            }
        });

        connection.onHasPiece(i -> {
            if (!connection.isInterested() && canRequestPiece(connection, i)) {
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

                            if (isEndGame()) {
                                enterEndGame();
                            } else {
                                requestNextPieces(connection);
                            }
                        })
                        .onSuccess(v -> {
                            processingPieces.remove(piece.getIndex());

                            torrentState.getBitfield().setPiece(piece.getIndex());

                            for (var conn : connections) {
                                conn.have(piece.getIndex());
                            }

                            if (isEndGame()) {
                                connections.forEach(conn -> conn.cancelPiece(piece.getIndex()));
                            }

                            if (torrentState.isTorrentComplete()) {
                                log.info("Download completed");

                                tracker.completed();

                                for (var conn : connections) {
                                    conn.notInterested();
                                }

                                // vertx.cancelTimer(timerId);
                            } else {
                                if (isEndGame()) {
                                    enterEndGame();
                                } else {
                                    requestNextPieces(connection);
                                }
                            }
                        });
            } else {
                // peer sent faulty piece
                log.warn("Received invalid piece for index {} from {}", piece.getIndex(), connection.getPeer());
                requestNextPieces(connection);
            }
        });

        connection.onUnchoked(v -> {
            requestNextPieces(connection);
        });

        connection.onClosed(v -> {
            connections.remove(connection);

            if (getLeechingPeersCount() < maxLeechingPeers) {
                unchokeNext();
            }
        });
    }

    public void assignConnection(PeerConnection connection) {
        connection.setTorrentState(torrentState);
        connection.handshake();

        if (torrentState.getBitfield().hasAnyPieces()) {
            connection.bitfield();
        }

        setupPeerConnection(connection);
    }
}
