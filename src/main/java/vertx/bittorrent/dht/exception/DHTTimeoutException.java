package vertx.bittorrent.dht.exception;

public class DHTTimeoutException extends RuntimeException {
    public DHTTimeoutException() {
        super("Query timed out");
    }
}
