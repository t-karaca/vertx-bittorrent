package vertx.bittorrent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.beust.jcommander.JCommander;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import vertx.bittorrent.dht.DHTClient;
import vertx.bittorrent.model.ClientOptions;

@Slf4j
public class ClientApplication {
    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

        ClientOptions clientOptions = new ClientOptions();
        JCommander.newBuilder().addObject(clientOptions).build().parse(args);

        // var vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1000 * 60 * 60));
        var vertx = Vertx.vertx();

        if (clientOptions.isDebug()) {
            Logger appLogger = (Logger) LoggerFactory.getLogger(ClientApplication.class.getPackageName());
            appLogger.setLevel(Level.DEBUG);

            if (!clientOptions.isDhtDebug()) {
                Logger dhtLogger = (Logger) LoggerFactory.getLogger(DHTClient.class.getPackageName());
                dhtLogger.setLevel(Level.INFO);
            }
        } else if (clientOptions.isDhtDebug()) {
            Logger dhtLogger = (Logger) LoggerFactory.getLogger(DHTClient.class.getPackageName());
            dhtLogger.setLevel(Level.DEBUG);
        }

        vertx.deployVerticle(new ClientVerticle(clientOptions));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }));
    }
}
