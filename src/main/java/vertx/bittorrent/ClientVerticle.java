package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final ClientOptions clientOptions;

    private ClientState clientState;
    private HttpServer httpServer;
    private HttpClient httpClient;

    private Map<HashKey, TorrentController> torrents = new HashMap<>();

    private List<SocketAddress> nodes = List.of();

    @Override
    public void start() throws Exception {
        log.info("Starting client");

        boolean isFreeRider = Boolean.parseBoolean(System.getenv("VB_IS_FREE_RIDER"));
        String nodesStr = System.getenv("OTHER_NODES");

        if (StringUtils.isNotBlank(nodesStr)) {
            nodes = Arrays.stream(nodesStr.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .map(s -> s.split(":"))
                    .map(a -> SocketAddress.inetSocketAddress(Integer.parseInt(a[1]), a[0]))
                    .peek(s -> log.info("Node: {}", s))
                    .toList();
        }

        var fs = vertx.fileSystem();

        // String torrentFileName = clientOptions.getTorrentFilePath();
        // if (!fs.existsBlocking(torrentFileName)) {
        //     log.error("Could not find torrent file at: {}", torrentFileName);
        //     vertx.close();
        //     return;
        // }

        fs.mkdirsBlocking("./data");
        fs.mkdirsBlocking("./torrents");

        clientState = new ClientState(vertx);

        httpClient = vertx.createHttpClient();

        httpServer = vertx.createHttpServer();

        httpServer.requestHandler(req -> {
            var response = req.response();

            req.bodyHandler(buf -> {
                log.info("Received buffer");

                try {
                    var torrent = Torrent.fromBuffer(buf);

                    HashKey key = new HashKey(torrent.getInfoHash());

                    if (!torrents.containsKey(key)) {
                        TorrentController controller = new TorrentController(vertx, clientState, clientOptions);
                        controller.start(torrent);
                        torrents.put(new HashKey(torrent.getInfoHash()), controller);
                    }

                    response.setStatusCode(200).end();
                } catch (Exception e) {
                    log.error("Unhandled exception", e);
                    response.setStatusCode(500).end();
                }
            });
        });

        httpServer
                .listen(54321)
                .onFailure(ex -> log.error("Could not start http server", ex))
                .onSuccess(server -> {
                    log.info("HTTP Server listening on port {}", server.actualPort());
                });

        vertx.executeBlocking(() -> {
                    if (!isFreeRider) {
                        log.info("Training model");
                        try {
                            LearningModel model = new LearningModel(
                                    clientOptions.getId(),
                                    clientOptions.getImagesPath(),
                                    clientOptions.getLabelsPath());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    return null;
                })
                .onFailure(ex -> log.error("Err", ex))
                .flatMap(v -> fs.readFile("./torrents/" + "node-" + clientOptions.getId() + "-weights.torrent"))
                .onSuccess(buffer -> {
                    for (SocketAddress address : nodes) {
                        httpClient
                                .request(HttpMethod.POST, address.port(), address.host(), "/")
                                .flatMap(req -> req.send(buffer))
                                .onFailure(ex -> log.error("Error", ex))
                                .onSuccess(res -> log.info("Received response {}", res.statusCode()));
                    }

                    // vertx.setPeriodic(0, 1_000, id -> {
                    //     scanTorrents();
                    // });

                    var torrent = Torrent.fromBuffer(buffer);

                    HashKey key = new HashKey(torrent.getInfoHash());

                    if (!torrents.containsKey(key)) {
                        TorrentController controller = new TorrentController(vertx, clientState, clientOptions);
                        controller.start(torrent);
                        torrents.put(new HashKey(torrent.getInfoHash()), controller);
                    }

                    vertx.setPeriodic(10_000, id -> {
                        checkFreeRiders();
                    });
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Shutting down client");

        Future.join(torrents.values().stream().map(t -> t.close()).toList()).onComplete(ar -> stopPromise.complete());
    }

    // private void scanTorrents() {
    //     log.debug("Checking directory '{}' for torrent files", clientOptions.getTorrentDir());
    //
    //     FileSystem fs = vertx.fileSystem();
    //
    //     fs.readDir(clientOptions.getTorrentDir()).onSuccess(files -> {
    //         log.debug("Found files: {}", files);
    //
    //         List<String> toRemove =
    //                 torrents.keySet().stream().filter(f -> !files.contains(f)).toList();
    //
    //         for (String f : toRemove) {
    //             TorrentController controller = torrents.remove(f);
    //             controller.close();
    //         }
    //
    //         files.stream()
    //                 .filter(f -> f.endsWith(".torrent"))
    //                 .filter(f -> !torrents.containsKey(f))
    //                 .forEach(f -> {
    //                     fs.readFile(f).map(Torrent::fromBuffer).onSuccess(torrent -> {
    //                         log.info("Starting controller for {}", f);
    //
    //                         TorrentController controller = new TorrentController(vertx, clientState, clientOptions);
    //                         controller.start(torrent);
    //                         torrents.put(f, controller);
    //                     });
    //                 });
    //     });
    // }

    private void checkFreeRiders() {
        for (PeerStats stats : clientState.getPeerStats().values()) {
            if (stats.getTotalDownloadedBytes() == 0) {
                if (stats.getTotalUploadedBytes() >= 2 * 1024 * 1024) {
                    stats.setFreeRider(true);

                    torrents.values().forEach(controller -> controller.handleFreeRider(stats.getAddress()));
                }
            }
        }
    }
}
