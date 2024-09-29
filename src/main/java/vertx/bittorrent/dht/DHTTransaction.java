package vertx.bittorrent.dht;

import io.vertx.core.Promise;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.dht.messages.DHTMessage;
import vertx.bittorrent.dht.messages.DHTQueryMessage;

@Getter
@Builder
public class DHTTransaction<T extends DHTMessage> {
    private final String transactionId;
    private final DHTNode target;
    private final DHTQueryMessage<T> queryMessage;
    private final long timerId;
    private final Promise<T> promise;
}
