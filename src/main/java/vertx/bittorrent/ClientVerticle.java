package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.file.FileSystem;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.dht.DHTClient;
import vertx.bittorrent.model.ClientOptions;
import vertx.bittorrent.model.Torrent;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final ClientOptions clientOptions;

    private ClientState clientState;
    private DHTClient dhtClient;

    private Map<String, TorrentController> torrents = new HashMap<>();

    @Override
    public void start() throws Exception {
        FileSystem fs = vertx.fileSystem();

        clientState = new ClientState(vertx);
        dhtClient = new DHTClient(vertx);

        if (clientOptions.getTorrentFilePath() == null) {
            return;
        }

        for (var filePath : clientOptions.getTorrentFilePath()) {
            fs.exists(filePath)
                    .onFailure(e -> log.error("Could not find torrent file at: {}", filePath))
                    .flatMap(exists -> fs.readFile(filePath))
                    .onFailure(e -> log.error("Error reading file", e))
                    .map(Torrent::fromBuffer)
                    .onFailure(e -> log.error("Error reading torrent file", e))
                    .onSuccess(torrent -> {
                        log.info("Starting controller for {}", filePath);

                        TorrentController controller =
                                new TorrentController(vertx, clientState, clientOptions, dhtClient);
                        controller.start(torrent);
                        torrents.put(filePath, controller);
                    });
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Shutting down client");

        Future.join(torrents.values().stream().map(t -> t.close()).toList()).onComplete(ar -> stopPromise.complete());
    }
}
