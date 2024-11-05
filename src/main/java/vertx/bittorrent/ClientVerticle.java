package vertx.bittorrent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.file.FileSystem;
import io.vertx.core.net.NetServer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.dht.DHTClient;
import vertx.bittorrent.model.ClientOptions;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.model.Peer;
import vertx.bittorrent.model.Torrent;

@Slf4j
@RequiredArgsConstructor
public class ClientVerticle extends AbstractVerticle {

    private final ClientOptions clientOptions;

    private ClientState clientState;
    private DHTClient dhtClient;

    private NetServer netServer;

    private Map<HashKey, TorrentController> torrents = new HashMap<>();

    @Override
    public void start() throws Exception {
        FileSystem fs = vertx.fileSystem();

        clientState = new ClientState(vertx);

        if (!clientOptions.isDhtDisable()) {
            dhtClient = new DHTClient(vertx, clientOptions, clientState);
        }

        if (clientOptions.getTorrentFilePaths() == null) {
            log.warn("No torrents specified. Starting in DHT only mode.");
            return;
        }

        netServer = vertx.createNetServer();
        netServer.connectHandler(socket -> {
            Peer peer = new Peer(socket.remoteAddress());
            log.debug("[{}] Peer connected", peer);

            PeerConnection connection = new PeerConnection(socket, clientState, null, peer);

            connection.onHandshake(handshake -> {
                if (Arrays.equals(handshake.getPeerId(), clientState.getPeerId())) {
                    // we connected to ourselves
                    connection.close();
                    return;
                }

                HashKey infoHash = new HashKey(handshake.getInfoHash());

                TorrentController controller = torrents.get(infoHash);
                if (controller != null) {
                    log.debug(
                            "[{}] Routing peer to torrent {}",
                            peer,
                            controller.getTorrentState().getTorrent().getName());

                    controller.assignConnection(connection);
                } else {
                    log.debug("[{}] Peer requested unknown torrent", peer);
                    // we are not serving the requested torrent
                    connection.close();
                }
            });
        });

        netServer
                .listen(clientOptions.getServerPort())
                .onFailure(e -> log.error("Could not start server: {}", e.getMessage()))
                .onSuccess(server -> {
                    log.info("BitTorrent listening on port {}", server.actualPort());
                    clientState.setServerPort(server.actualPort());
                });

        for (var filePath : clientOptions.getTorrentFilePaths()) {
            fs.exists(filePath)
                    .onFailure(e -> log.error("Could not find torrent file at: {}", filePath))
                    .flatMap(exists -> fs.readFile(filePath))
                    .onFailure(e -> log.error("Error reading file", e))
                    .map(Torrent::fromBuffer)
                    .onFailure(e -> log.error("Error reading torrent file", e))
                    .onSuccess(torrent -> {
                        log.info("Initializing torrent for {}", filePath);

                        TorrentController controller =
                                new TorrentController(vertx, clientState, clientOptions, dhtClient);
                        controller.start(torrent);
                        torrents.put(new HashKey(torrent.getInfoHash()), controller);
                    });
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Shutting down client");

        Future.join(torrents.values().stream().map(t -> t.close()).toList())
                .flatMap(v -> netServer.close())
                .onComplete(ar -> stopPromise.complete());
    }
}
