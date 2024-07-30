package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final String torrentFileName;

    private final ClientState clientState = new ClientState();
    private final List<PeerConnection> connections = new ArrayList<>();

    private NetClient netClient;
    private HttpClient httpClient;

    private long totalBytesDownloaded = 0L;
    private long timerId;

    private Map<String, AsyncFile> fileMap = new HashMap<>();

    private Set<Integer> processingPieces = new HashSet<>();

    @Override
    public void start() throws Exception {
        netClient = vertx.createNetClient();
        httpClient = vertx.createHttpClient();

        vertx.fileSystem()
                .readFile(torrentFileName)
                .onFailure(e -> log.error("Error reading file:", e))
                .map(Torrent::fromBuffer)
                .onFailure(e -> log.error("Error parsing torrent info:", e))
                .onSuccess(clientState::setTorrent)
                .onSuccess(torrent -> {
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

    private Future<Void> writePieceToFile(Piece piece) {
        int processedBytes = 0;
        int pieceLength = piece.getData().length();

        List<Future<Void>> futures = new ArrayList<>();

        while (processedBytes < pieceLength) {
            FilePosition position = clientState.getTorrent().getFilePositionForBlock(piece.getIndex(), processedBytes);

            AsyncFile file = fileMap.computeIfAbsent(position.getFileInfo().getPath(), path -> {
                try {
                    Path parent = Path.of(path).getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    return vertx.fileSystem()
                            .openBlocking(path, new OpenOptions().setRead(true).setWrite(true));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            int bytesToWrite = (int)
                    Math.min(position.getFileInfo().getLength() - position.getOffset(), pieceLength - processedBytes);

            futures.add(file.write(
                    piece.getData().slice(processedBytes, processedBytes + bytesToWrite), position.getOffset()));

            processedBytes += bytesToWrite;
        }

        return Future.all(futures).mapEmpty();
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

                    writePieceToFile(piece)
                            .onFailure(ex -> {
                                log.error("Could not write piece to file", ex);
                                processingPieces.remove(piece.getIndex());
                                requestNextPiece(connection);
                            })
                            .onSuccess(v -> {
                                processingPieces.remove(piece.getIndex());

                                clientState.getBitfield().setPiece(piece.getIndex());

                                if (clientState.getBitfield().cardinality()
                                        == clientState.getTorrent().getPiecesCount()) {
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
