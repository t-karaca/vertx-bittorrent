package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetClient;
import java.net.URI;
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
    private NetClient netClient;
    private HttpClient httpClient;

    private long totalBytesDownloaded = 0L;
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

        netClient = vertx.createNetClient();
        httpClient = vertx.createHttpClient();

        clientState.checkPiecesOnDisk().onSuccess(v -> {
            announceToTracker(torrent).onSuccess(response -> {
                for (Peer peer : response.getPeers()) {
                    connectToPeer(peer);
                }
            });
        });

        timerId = vertx.setPeriodic(1_000, id -> {
            double totalDownloadRate = 0.0f;

            for (var connection : connections) {
                int deltaBytes = connection.getBytesDownloaded() - connection.getPreviousBytesDownloaded();

                totalBytesDownloaded += deltaBytes;
                totalDownloadRate += deltaBytes;

                connection.setDownloadRate(deltaBytes);

                connection.setPreviousBytesDownloaded(connection.getBytesDownloaded());
            }

            double downloadedRatio =
                    totalBytesDownloaded / (double) clientState.getTorrent().getLength();

            String progress = String.format("%.02f", downloadedRatio * 100.0);

            log.info(
                    "Download progress: {}% ({}MB / {}MB) ({}KB/s)",
                    progress,
                    // downloadedRatio * 100.0,
                    totalBytesDownloaded / 1024 / 1024,
                    clientState.getTorrent().getLength() / 1024 / 1024,
                    totalDownloadRate / 1024);
        });
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

    private Future<TrackerResponse> announceToTracker(Torrent torrent) {
        URI uri = UriBuilder.fromUriString(torrent.getAnnounce())
                .queryParam("info_hash", torrent.getInfoHash())
                .rawQueryParam("port", "12345")
                .queryParam("peer_id", clientState.getPeerId())
                .rawQueryParam("uploaded", "0")
                .rawQueryParam("downloaded", "0")
                .rawQueryParam("left", String.valueOf(torrent.getLength()))
                .build();

        log.info("Tracker URI: {}", uri);

        return httpClient
                .request(new RequestOptions().setAbsoluteURI(uri.toString()))
                .flatMap(HttpClientRequest::send)
                .flatMap(this::checkResponse)
                .flatMap(HttpClientResponse::body)
                .map(TrackerResponse::fromBuffer)
                .onFailure(e -> log.error("Error while requesting from tracker:", e));
    }

    private Future<HttpClientResponse> checkResponse(HttpClientResponse response) {
        if (response.statusCode() >= 400) {
            return response.body()
                    .map(buffer -> buffer.getString(0, buffer.length()))
                    .flatMap(value -> Future.failedFuture(
                            new Exception("Request failed with status " + response.statusCode() + ": " + value)));
        } else {
            return Future.succeededFuture(response);
        }
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
