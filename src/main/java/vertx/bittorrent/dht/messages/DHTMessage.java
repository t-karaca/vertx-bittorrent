package vertx.bittorrent.dht.messages;

import static java.util.Objects.requireNonNull;

import be.adaxisoft.bencode.BEncodedValue;
import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.Setter;
import vertx.bittorrent.BEncodedDict;

public class DHTMessage {

    public static final String KEY_TRANSACTION_ID = "t";
    public static final String KEY_MESSAGE_TYPE = "y";
    public static final String KEY_QUERY_TYPE = "q";

    @Getter
    @Setter
    private String transactionId;

    @Getter
    @Setter
    private DHTMessageType type;

    @Getter
    @Setter
    private Payload payload;

    public boolean isQuery() {
        return type == DHTMessageType.QUERY;
    }

    public boolean isResponse() {
        return type == DHTMessageType.RESPONSE;
    }

    public boolean isError() {
        return type == DHTMessageType.ERROR;
    }

    public Buffer toBuffer() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(KEY_TRANSACTION_ID, transactionId);
        dict.put(KEY_MESSAGE_TYPE, type.value);

        if (payload instanceof QueryPayload queryPayload) {
            String queryType = queryPayload.queryType();
            requireNonNull(queryType);

            dict.put(KEY_QUERY_TYPE, queryType);
        }

        if (payload != null) {
            BEncodedValue value = payload.value();

            if (value != null) {
                dict.put(type.payloadKey, value);
            }
        }

        ByteBuffer buffer = dict.encode();
        return Buffer.buffer(buffer.array());
    }
}
