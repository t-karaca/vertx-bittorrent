package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.file.FileSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.dht.DHTClient;
import vertx.bittorrent.model.ClientOptions;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final ClientOptions clientOptions;

    private ClientState clientState;
    private DHTClient dhtClient;

    private List<String> completedTorrents = new ArrayList<>();

    private Map<String, TorrentController> torrents = new HashMap<>();

    @Override
    public void start() throws Exception {
        FileSystem fs = vertx.fileSystem();

        String torrentFileName = clientOptions.getTorrentFilePath();
        if (!fs.existsBlocking(torrentFileName)) {
            log.error("Could not find torrent file at: {}", torrentFileName);
            vertx.close();
            return;
        }

        clientState = new ClientState(vertx);
        dhtClient = new DHTClient(vertx);

        fs.readFile(torrentFileName)
                .onFailure(e -> log.error("Error reading file", e))
                .map(Torrent::fromBuffer)
                .onFailure(e -> log.error("Error reading torrent file", e))
                .onSuccess(torrent -> {
                    log.info("Starting controller for {}", torrentFileName);

                    TorrentController controller = new TorrentController(vertx, clientState, clientOptions, dhtClient);
                    controller.start(torrent);
                    torrents.put(torrentFileName, controller);
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Shutting down client");

        Future.join(torrents.values().stream().map(t -> t.close()).toList()).onComplete(ar -> stopPromise.complete());
    }
}
