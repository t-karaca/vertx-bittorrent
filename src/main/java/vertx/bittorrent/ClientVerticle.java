package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.NetClient;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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

    private AsyncFile file;

    @Override
    public void start() throws Exception {
        netClient = vertx.createNetClient();
        httpClient = vertx.createHttpClient();

        file = vertx.fileSystem()
                .openBlocking("testfile.tar.gz", new OpenOptions().setRead(true).setWrite(true));

        vertx.fileSystem()
                .readFile(torrentFileName)
                .onFailure(e -> log.error("Error reading file:", e))
                .map(Torrent::fromBuffer)
                .onFailure(e -> log.error("Error parsing torrent info:", e))
                .onSuccess(clientState::setTorrent)
                .flatMap(this::announceToTracker)
                .onSuccess(response -> {
                    for (Peer peer : response.getPeers()) {
                        PeerConnection.connect(netClient, clientState, peer).onSuccess(connection -> {
                            connections.add(connection);

                            connection.onBitfield(bitfield -> {
                                connection.interested();
                            });

                            connection.onBlockReceived(block -> {
                                // TODO: validate piece index and begin offset

                                if (block.getData().length() > ProtocolHandler.MAX_BLOCK_SIZE) {
                                    log.error("Data from received block larger than expected");
                                    return;
                                }

                                long offset = clientState.getTorrent().getPieceLength() * block.getPieceIndex()
                                        + block.getBegin();

                                file.write(block.getData(), offset)
                                        .onFailure(ex -> log.error("Could not write block to file", ex));
                            });

                            connection.onPieceCompleted(i -> {
                                clientState.getBitfield().setPiece(i);

                                if (clientState.getBitfield().cardinality()
                                        == clientState.getTorrent().getPiecesCount()) {
                                    log.info("Download completed");

                                    vertx.cancelTimer(timerId);
                                }

                                requestNextPiece(connection);
                            });

                            connection.onUnchoked(v -> {
                                requestNextPiece(connection);
                            });

                            connection.onClosed(v -> connections.remove(connection));
                        });
                    }
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
                    && !isPieceRequested(i)) {

                log.debug("Requesting piece {} from peer {}", i, connection.getPeer());
                connection.requestPiece(i);
                break;
            }
        }
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
                .rawQueryParam("compact", "1")
                .build();

        int port = uri.getPort();

        if (port == -1) {
            if ("http".equals(uri.getScheme())) {
                port = 80;
            } else if ("https".equals(uri.getScheme())) {
                port = 443;
            }
        }

        return httpClient
                .request(HttpMethod.GET, port, uri.getHost(), uri.getRawPath() + "?" + uri.getRawQuery())
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
}
