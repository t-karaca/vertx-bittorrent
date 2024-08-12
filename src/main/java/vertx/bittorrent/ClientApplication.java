package vertx.bittorrent;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientApplication {
    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

        if (args.length < 1) {
            log.error("No arguments specified.\n\nUsage: vertx-bittorrent <file>");
            System.exit(1);
        }

        var vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1000 * 60 * 60));

        vertx.deployVerticle(new ClientVerticle(args[0]));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }));
    }
}
