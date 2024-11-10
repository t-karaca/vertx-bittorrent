package vertx.bittorrent.dht;

import be.adaxisoft.bencode.BDecoder;
import be.adaxisoft.bencode.BEncodedValue;
import io.vertx.core.Future;
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
import vertx.bittorrent.dht.messages.AnnouncePeerQuery;
import vertx.bittorrent.dht.messages.DHTErrorMessage;
import vertx.bittorrent.dht.messages.DHTMessage;
import vertx.bittorrent.dht.messages.DHTMessageType;
import vertx.bittorrent.dht.messages.FindNodeQuery;
import vertx.bittorrent.dht.messages.GetPeersQuery;
import vertx.bittorrent.dht.messages.GetQuery;
import vertx.bittorrent.dht.messages.Payload;
import vertx.bittorrent.dht.messages.PingQuery;
import vertx.bittorrent.dht.messages.PutQuery;
import vertx.bittorrent.dht.messages.QueryPayload;
import vertx.bittorrent.model.ClientOptions;

@Slf4j
public class DHTProtocolHandler {

    private final Vertx vertx;

    private final DatagramSocket socket;

    private final Map<String, DHTTransaction<?>> activeTransactions = new HashMap<>();

    private final Random random = new SecureRandom();

    private final Map<Class<? extends QueryPayload>, BiFunction<SocketAddress, QueryPayload, ? extends Payload>>
            queryHandlers = new HashMap<>();

    public DHTProtocolHandler(Vertx vertx, ClientOptions clientOptions) {
        this.vertx = vertx;

        this.socket = vertx.createDatagramSocket();

        this.socket
                .listen(clientOptions.getDhtPort(), "0.0.0.0")
                .onFailure(e -> log.error("Error", e))
                .onSuccess(socket -> {
                    log.info("DHT listening on port {}", clientOptions.getDhtPort());

                    socket.handler(packet -> {
                        SocketAddress sender = packet.sender();

                        readBuffer(sender, packet.data());
                    });
                });
    }

    public Future<Void> close() {
        return this.socket.close();
    }

    public <R extends Payload, T extends QueryPayload<R>> void onQuery(
            Class<T> type, BiFunction<SocketAddress, T, R> handler) {
        queryHandlers.put(type, (BiFunction) handler);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void readBuffer(SocketAddress sender, Buffer buffer) {
        try {
            BEncodedDict dict = new BEncodedDict(BDecoder.bdecode(ByteBuffer.wrap(buffer.getBytes())));

            String transactionId = dict.requireString(DHTMessage.KEY_TRANSACTION_ID);
            String key = dict.requireString(DHTMessage.KEY_MESSAGE_TYPE);

            DHTMessageType type = DHTMessageType.fromValue(key);

            BEncodedValue value = dict.requireBEncodedValue(type.payloadKey);

            switch (type) {
                case QUERY: {
                    String queryType = dict.requireString(DHTMessage.KEY_QUERY_TYPE);

                    QueryPayload<?> payload =
                            switch (queryType) {
                                case PingQuery.QUERY_TYPE -> PingQuery.from(value);
                                case FindNodeQuery.QUERY_TYPE -> FindNodeQuery.from(value);
                                case GetPeersQuery.QUERY_TYPE -> GetPeersQuery.from(value);
                                case AnnouncePeerQuery.QUERY_TYPE -> AnnouncePeerQuery.from(value);
                                case GetQuery.QUERY_TYPE -> GetQuery.from(value);
                                case PutQuery.QUERY_TYPE -> PutQuery.from(value);
                                default -> null;
                            };

                    log.debug("Received query from {} : {}", sender, payload);

                    if (payload != null) {
                        var handler = queryHandlers.get(payload.getClass());

                        DHTMessage response = new DHTMessage().setTransactionId(transactionId);

                        try {
                            response.setPayload(handler.apply(sender, payload));
                            response.setType(DHTMessageType.RESPONSE);
                        } catch (DHTErrorException e) {
                            log.error("Error while processing query", e);

                            response.setPayload(e.getErrorMessage());
                            response.setType(DHTMessageType.ERROR);
                        }

                        log.debug("Sending response to {} : {}", sender, response.getPayload());

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

                        Payload m = transaction.getQueryMessage().parseResponse(value);

                        log.debug("Received response from {} : {}", sender, m);

                        transaction.complete(m);
                    }

                    break;
                }
                case ERROR: {
                    DHTTransaction transaction = activeTransactions.get(transactionId);

                    if (transaction != null) {
                        if (transaction.getTarget() != null) {
                            transaction.getTarget().addFailedQuery();
                        }

                        DHTErrorMessage m = DHTErrorMessage.from(value);

                        log.debug("Received error from {} : {}", sender, m);

                        transaction.fail(new DHTErrorException(m));
                    }

                    break;
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T extends Payload> Future<T> query(SocketAddress address, QueryPayload<T> queryMessage) {
        return startTransaction(address, queryMessage).future();
    }

    public <T extends Payload> Future<T> query(DHTNode node, QueryPayload<T> queryMessage) {
        return startTransaction(node.getAddress(), queryMessage).setTarget(node).future();
    }

    private <T extends Payload> DHTTransaction<T> startTransaction(
            SocketAddress address, QueryPayload<T> queryMessage) {
        String transactionId = generateTransactionId();

        var transaction = new DHTTransaction<>(vertx, queryMessage) //
                .onComplete(v -> activeTransactions.remove(transactionId));

        activeTransactions.put(transactionId, transaction);

        DHTMessage message = new DHTMessage()
                .setTransactionId(transactionId)
                .setType(DHTMessageType.QUERY)
                .setPayload(queryMessage);

        log.debug("Sending query to {} : {}", address, queryMessage);

        Buffer packet = message.toBuffer();

        socket.send(packet, address.port(), address.hostAddress()).onFailure(transaction::fail);

        return transaction;
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
