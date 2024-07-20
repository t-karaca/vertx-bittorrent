package vertx.bittorrent;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientApplication {
    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");
        log.info("Starting application with args: {}", (Object) args);

        var vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1000 * 60 * 60));

        // vertx.createNetServer().listen().onSuccess(server -> {
        //     server.actualPort();
        // });

        vertx.deployVerticle(new ClientVerticle("test.torrent"));

        // String peerId = "01234567890123456789";
        //
        // var httpClient = vertx.createHttpClient(new HttpClientOptions().setLogActivity(true));

        // vertx.fileSystem().readFile("test.torrent").map(Torrent::fromBuffer).flatMap(torrent -> {
        //     URI uri = UriBuilder.fromUriString(torrent.getAnnounce())
        //             .rawQueryParam("info_hash", torrent.getUrlEncodedInfoHash())
        //             .rawQueryParam("port", "12345")
        //             .rawQueryParam("peer_id", peerId)
        //             .rawQueryParam("uploaded", "0")
        //             .rawQueryParam("downloaded", "0")
        //             .rawQueryParam("left", String.valueOf(torrent.getLength()))
        //             .rawQueryParam("compact", "1")
        //             .build();
        //
        //     int port = uri.getPort();
        //
        //     if (port == -1) {
        //         if ("http".equals(uri.getScheme())) {
        //             port = 80;
        //         } else if ("https".equals(uri.getScheme())) {
        //             port = 443;
        //         }
        //     }
        //
        //     return httpClient
        //             .request(HttpMethod.GET, port, uri.getHost(), uri.getRawPath() + "?" + uri.getRawQuery())
        //             .flatMap(HttpClientRequest::send)
        //             .andThen(ar -> {
        //                 log.info("completed");
        //                 if (ar.succeeded()) {
        //                     log.info("succeeded");
        //                     var response = ar.result();
        //                     log.info("status: {}", response.statusCode());
        //                     if (response.statusCode() >= 400) {
        //                         throw new IllegalStateException("Response status: " + response.statusCode());
        //                     }
        //                 } else if (ar.failed()) {
        //                     log.error("Failed");
        //                 }
        //             })
        //             .onFailure(e -> log.error("Exception:", e))
        //             .onSuccess(response -> log.info("Request success: {}", response.statusCode()))
        //             .flatMap(HttpClientResponse::body)
        //             .map(TrackerResponse::fromBuffer)
        //             .onSuccess(response -> log.info("Received tracker response: {}", response))
        //             .onSuccess(response -> {
        //                 log.info("building connections");
        //                 for (Peer peer : response.getPeers()) {
        //                     log.info("Peer: {}", peer);
        //
        //                     if (peer.getPort() == 12345) continue;
        //
        //                     var conn = PeerConnection.create(vertx, peer);
        //                     conn.connect().onSuccess(v -> {
        //                         conn.handshake(torrent.getInfoHash(), peerId.getBytes(StandardCharsets.UTF_8))
        //                                 .onSuccess(r -> log.info("Sent handshake"));
        //                     });
        //                 }
        //             });
        // });
    }
}
