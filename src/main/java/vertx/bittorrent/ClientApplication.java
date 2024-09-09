package vertx.bittorrent;

import com.beust.jcommander.JCommander;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientApplication {
    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

        ClientOptions clientOptions = new ClientOptions();
        JCommander.newBuilder().addObject(clientOptions).build().parse(args);

        // if (StringUtils.isBlank(clientOptions.getTorrentFilePath())) {
        //     log.error("No arguments specified.\n\nUsage: vertx-bittorrent <file>");
        //     System.exit(1);
        // }

        // try {
        //     LearningModel model = new LearningModel(
        //             clientOptions.getId(), clientOptions.getImagesPath(), clientOptions.getLabelsPath());
        // } catch (IOException e) {
        //     throw new RuntimeException(e);
        // }

        var vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1000 * 60 * 60));

        // if (!vertx.fileSystem().existsBlocking(clientOptions.getTorrentDir())) {
        // }
        // var torrents = vertx.fileSystem().readDirBlocking(clientOptions.getTorrentDir());
        //
        // for (var torrent : torrents) {
        //     vertx.deployVerticle(new ClientVerticle(clientOptions, torrent));
        // }

        vertx.deployVerticle(new ClientVerticle(clientOptions));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }));
    }
}
