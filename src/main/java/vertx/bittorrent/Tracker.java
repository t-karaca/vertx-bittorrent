package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class Tracker {

    private final Vertx vertx;
    private final ClientState clientState;

    private final HttpClient httpClient;

    private long timerId;

    private Handler<List<Peer>> peersHandler;

    public Tracker(Vertx vertx, ClientState clientState) {
        this.vertx = vertx;
        this.clientState = clientState;

        this.httpClient = vertx.createHttpClient();
    }

    public Tracker onPeersReceived(Handler<List<Peer>> handler) {
        peersHandler = handler;
        return this;
    }

    public Future<Void> announce() {
        return request("started")
                .onSuccess(response -> {
                    timerId = vertx.setPeriodic(response.getInterval() * 1000, id -> request(null));
                })
                .mapEmpty();
    }

    public Future<Void> completed() {
        return request("completed").mapEmpty();
    }

    public Future<Void> close() {
        vertx.cancelTimer(timerId);

        return request("stopped").mapEmpty();
    }

    private Future<TrackerResponse> request(String event) {
        Torrent torrent = clientState.getTorrent();

        UriBuilder builder = UriBuilder.fromUriString(torrent.getAnnounce())
                .queryParam("info_hash", torrent.getInfoHash())
                .queryParam("port", clientState.getServerPort())
                .queryParam("peer_id", clientState.getPeerId())
                .queryParam("uploaded", clientState.getTotalBytesUploaded())
                .queryParam("downloaded", clientState.getTotalBytesDownloaded())
                .queryParam("left", clientState.getRemainingBytes());

        if (StringUtils.isNotBlank(event)) {
            builder.queryParam("event", event);
        }

        URI uri = builder.build();

        log.info("Tracker URI: {}", uri);

        return httpClient
                .request(new RequestOptions().setAbsoluteURI(uri.toString()))
                .flatMap(HttpClientRequest::send)
                .flatMap(this::checkResponse)
                .flatMap(HttpClientResponse::body)
                .map(TrackerResponse::fromBuffer)
                .onFailure(e -> log.error("Error while requesting from tracker:", e))
                .onSuccess(response -> {
                    if (peersHandler != null) {
                        peersHandler.handle(response.getPeers());
                    }
                });
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
