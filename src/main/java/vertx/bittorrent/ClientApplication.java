package vertx.bittorrent;

import io.vertx.core.Vertx;

public class ClientApplication {
    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");
        var vertx = Vertx.vertx();
    }
}
