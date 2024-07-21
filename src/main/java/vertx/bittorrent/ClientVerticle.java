package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
                .flatMap(this::announceToTracker)
                .onSuccess(response -> {
                    for (Peer peer : response.getPeers()) {
                        PeerConnection.connect(netClient, clientState, peer).onSuccess(connection -> {
                            connections.add(connection);

                            connection.onBitfield(bitfield -> {
                                connection.interested();
                            });

                            connection.onPieceCompleted(i -> {
                                clientState.getBitfield().setPiece(i);

                                requestNextPiece(connection);
                            });

                            connection.onUnchoked(v -> {
                                requestNextPiece(connection);
                            });

                            connection.onClosed(v -> connections.remove(connection));
                        });
                    }
                });

        vertx.setPeriodic(1_000, id -> {
            for (var connection : connections) {
                int deltaBytes = connection.getBytesDownloaded() - connection.getPreviousBytesDownloaded();

                connection.setDownloadRate(deltaBytes);

                connection.setPreviousBytesDownloaded(connection.getBytesDownloaded());
            }
        });
    }

    private void requestNextPiece(PeerConnection connection) {
        for (int i = 0; i < clientState.getTorrent().getPiecesCount(); i++) {
            if (!clientState.getBitfield().hasPiece(i)
                    && connection.getBitfield().hasPiece(i)
                    && !isPieceRequested(i)) {

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
