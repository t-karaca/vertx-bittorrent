package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.file.FileSystem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final ClientOptions clientOptions;

    private ClientState clientState;

    private List<String> completedTorrents = new ArrayList<>();

    private Map<String, TorrentController> torrents = new HashMap<>();

    @Override
    public void start() throws Exception {

        boolean isFreeRider = Boolean.parseBoolean(System.getenv("VB_IS_FREE_RIDER"));

        // String torrentFileName = clientOptions.getTorrentFilePath();
        // if (!fs.existsBlocking(torrentFileName)) {
        //     log.error("Could not find torrent file at: {}", torrentFileName);
        //     vertx.close();
        //     return;
        // }

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
                .onSuccess(o -> {
                    clientState = new ClientState(vertx);

                    vertx.setPeriodic(0, 1_000, id -> {
                        scanTorrents();
                    });

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

    private void scanTorrents() {
        log.debug("Checking directory '{}' for torrent files", clientOptions.getTorrentDir());

        FileSystem fs = vertx.fileSystem();

        fs.readDir(clientOptions.getTorrentDir()).onSuccess(files -> {
            log.debug("Found files: {}", files);

            List<String> toRemove =
                    torrents.keySet().stream().filter(f -> !files.contains(f)).toList();

            for (String f : toRemove) {
                TorrentController controller = torrents.remove(f);
                controller.close();
            }

            files.stream()
                    .filter(f -> f.endsWith(".torrent"))
                    .filter(f -> !torrents.containsKey(f))
                    .forEach(f -> {
                        fs.readFile(f).map(Torrent::fromBuffer).onSuccess(torrent -> {
                            log.info("Starting controller for {}", f);

                            TorrentController controller = new TorrentController(vertx, clientState, clientOptions);
                            controller.start(torrent);
                            torrents.put(f, controller);
                        });
                    });
        });
    }

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
