package vertx.bittorrent.dht;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.Setter;
import vertx.bittorrent.dht.exception.DHTTimeoutException;
import vertx.bittorrent.dht.messages.Payload;
import vertx.bittorrent.dht.messages.QueryPayload;

public class DHTTransaction<T extends Payload> {
    private final Vertx vertx;
    private final long timerId;
    private final Promise<T> promise;

    @Getter
    private final QueryPayload<T> queryMessage;

    @Getter
    @Setter
    private DHTNode target;

    private Handler<Void> completeHandler;

    public DHTTransaction(Vertx vertx, QueryPayload<T> payload) {
        this.vertx = vertx;
        this.queryMessage = payload;

        promise = Promise.promise();

        timerId = vertx.setTimer(10_000, i -> {
            fail(new DHTTimeoutException());

            if (target != null) {
                target.addFailedQuery();
            }
        });
    }

    public DHTTransaction<T> onComplete(Handler<Void> handler) {
        completeHandler = handler;
        return this;
    }

    public Future<T> future() {
        return promise.future();
    }

    public void complete(T payload) {
        vertx.cancelTimer(timerId);

        promise.complete(payload);

        if (completeHandler != null) {
            completeHandler.handle(null);
        }
    }

    public void fail(Throwable cause) {
        vertx.cancelTimer(timerId);

        promise.fail(cause);

        if (completeHandler != null) {
            completeHandler.handle(null);
        }
    }
}
