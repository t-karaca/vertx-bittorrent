package vertx.bittorrent.dht;

import be.adaxisoft.bencode.BDecoder;
import be.adaxisoft.bencode.BEncodedValue;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.net.SocketAddress;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.dht.exception.DHTErrorException;
import vertx.bittorrent.dht.exception.DHTTimeoutException;
import vertx.bittorrent.dht.messages.DHTAnnouncePeerQuery;
import vertx.bittorrent.dht.messages.DHTErrorMessage;
import vertx.bittorrent.dht.messages.DHTFindNodeQuery;
import vertx.bittorrent.dht.messages.DHTGetPeersQuery;
import vertx.bittorrent.dht.messages.DHTMessage;
import vertx.bittorrent.dht.messages.DHTMessage.Type;
import vertx.bittorrent.dht.messages.DHTPingQuery;
import vertx.bittorrent.dht.messages.DHTQueryMessage;

@Slf4j
public class DHTProtocolHandler {

    private final Vertx vertx;

    private final DatagramSocket socket;

    private final Map<String, DHTTransaction<?>> activeTransactions = new HashMap<>();

    private final Random random = new SecureRandom();

    private final Map<
                    Class<? extends DHTQueryMessage>, BiFunction<SocketAddress, DHTQueryMessage, ? extends DHTMessage>>
            queryHandlers = new HashMap<>();

    public DHTProtocolHandler(Vertx vertx) {
        this.vertx = vertx;
        this.socket = vertx.createDatagramSocket();

        this.socket
                .listen(6881, "0.0.0.0")
                .onFailure(e -> log.error("Error", e))
                .onSuccess(socket -> {
                    log.info("DHT listening on port 6881");

                    socket.handler(packet -> {
                        SocketAddress sender = packet.sender();

                        readBuffer(sender, packet.data());
                    });
                });
    }

    public Future<Void> close() {
        return this.socket.close();
    }

    public <R extends DHTMessage, T extends DHTQueryMessage<R>> void onQuery(
            Class<T> type, BiFunction<SocketAddress, T, R> handler) {
        queryHandlers.put(type, (BiFunction) handler);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void readBuffer(SocketAddress sender, Buffer buffer) {
        try {
            BEncodedDict dict = new BEncodedDict(BDecoder.bdecode(ByteBuffer.wrap(buffer.getBytes())));

            String transactionId = dict.requireString(DHTMessage.KEY_TRANSACTION_ID);
            String key = dict.requireString(DHTMessage.KEY_MESSAGE_TYPE);

            Type type = Type.fromKey(key);

            BEncodedValue payload = dict.requireBEncodedValue(type.getPayloadKey());

            switch (type) {
                case QUERY: {
                    String queryType = dict.requireString(DHTQueryMessage.KEY_QUERY_TYPE);

                    DHTQueryMessage<?> message =
                            switch (queryType) {
                                case "ping" -> DHTPingQuery.fromPayload(transactionId, payload);
                                case "find_node" -> DHTFindNodeQuery.fromPayload(transactionId, payload);
                                case "get_peers" -> DHTGetPeersQuery.fromPayload(transactionId, payload);
                                case "announce_peer" -> DHTAnnouncePeerQuery.fromPayload(transactionId, payload);
                                default -> null;
                            };

                    log.debug("Received query from {}:{} : {}", sender.hostAddress(), sender.port(), message);

                    if (message != null) {
                        var handler = queryHandlers.get(message.getClass());

                        DHTMessage response;

                        try {
                            response = handler.apply(sender, message);
                        } catch (DHTErrorException e) {
                            response = e.getErrorMessage();
                        }

                        response.setTransactionId(transactionId);

                        log.debug("Sending response to {}:{} : {}", sender.hostAddress(), sender.port(), response);
                        Buffer packet = response.toBuffer();
                        socket.send(packet, sender.port(), sender.hostAddress());
                    }

                    break;
                }
                case RESPONSE: {
                    DHTTransaction transaction = activeTransactions.get(transactionId);

                    if (transaction != null) {
                        if (transaction.getTarget() != null) {
                            transaction.getTarget().clearFailedQueries();
                            transaction.getTarget().refresh();
                        }

                        DHTMessage m = transaction.getQueryMessage().parseResponse(payload);

                        log.debug("Received response from {}:{} : {}", sender.hostAddress(), sender.port(), m);

                        vertx.cancelTimer(transaction.getTimerId());

                        transaction.getPromise().complete(m);

                        activeTransactions.remove(transactionId);
                    }

                    break;
                }
                case ERROR: {
                    DHTTransaction transaction = activeTransactions.get(transactionId);

                    if (transaction != null) {
                        DHTErrorMessage m = DHTErrorMessage.fromPayload(transactionId, payload);

                        log.debug("Received error from {}:{} : {}", sender.hostAddress(), sender.port(), m);

                        vertx.cancelTimer(transaction.getTimerId());

                        transaction.getPromise().fail(new DHTErrorException(m));

                        activeTransactions.remove(transactionId);
                    }

                    break;
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T extends DHTMessage> Future<T> query(SocketAddress address, DHTQueryMessage<T> queryMessage) {
        String transactionId = generateTransactionId();

        queryMessage.setTransactionId(transactionId);

        log.debug("Sending query to {}:{} : {}", address.hostAddress(), address.port(), queryMessage);

        Buffer packet = queryMessage.toBuffer();

        return socket.send(packet, address.port(), address.hostAddress())
                .map(v -> {
                    Promise<T> promise = Promise.promise();

                    long timerId = vertx.setTimer(10_000, id -> {
                        promise.fail(new DHTTimeoutException());

                        activeTransactions.remove(transactionId);
                    });

                    DHTTransaction<T> transaction = DHTTransaction.<T>builder()
                            .transactionId(transactionId)
                            .queryMessage(queryMessage)
                            .promise(promise)
                            .timerId(timerId)
                            .build();

                    activeTransactions.put(transactionId, transaction);

                    return transaction;
                })
                .flatMap(transaction -> transaction.getPromise().future());
    }

    public <T extends DHTMessage> Future<T> query(DHTNode node, DHTQueryMessage<T> queryMessage) {
        String transactionId = generateTransactionId();

        queryMessage.setTransactionId(transactionId);

        log.debug(
                "Sending query to {}:{} : {}",
                node.getAddress().hostAddress(),
                node.getAddress().port(),
                queryMessage);

        Buffer packet = queryMessage.toBuffer();

        return socket.send(packet, node.getAddress().port(), node.getAddress().hostAddress())
                .map(v -> {
                    Promise<T> promise = Promise.promise();

                    long timerId = vertx.setTimer(10_000, id -> {
                        promise.fail(new DHTTimeoutException());

                        node.addFailedQuery();

                        activeTransactions.remove(transactionId);
                    });

                    DHTTransaction<T> transaction = DHTTransaction.<T>builder()
                            .transactionId(transactionId)
                            .queryMessage(queryMessage)
                            .target(node)
                            .promise(promise)
                            .timerId(timerId)
                            .build();

                    activeTransactions.put(transactionId, transaction);

                    return transaction;
                })
                .flatMap(transaction -> transaction.getPromise().future());
    }

    private String generateTransactionId() {
        String transactionId;

        byte[] bytes = new byte[2];

        do {
            random.nextBytes(bytes);

            transactionId = new String(bytes);
        } while (activeTransactions.containsKey(transactionId));

        return transactionId;
    }
}
