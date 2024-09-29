package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.experimental.SuperBuilder;
import vertx.bittorrent.BEncodedDict;

@SuperBuilder
public abstract class DHTQueryMessage<T extends DHTMessage> extends DHTMessage {
    public static final String KEY_QUERY_TYPE = "q";

    public abstract Class<T> getResponseClass();

    public abstract String getQueryType();

    public abstract T parseResponse(BEncodedValue payload);

    @Override
    public Type getMessageType() {
        return Type.QUERY;
    }

    @Override
    public Buffer toBuffer() {
        BEncodedDict dict = new BEncodedDict();

        Type type = getMessageType();

        dict.put(KEY_TRANSACTION_ID, getTransactionId());
        dict.put(KEY_MESSAGE_TYPE, type.getKey());
        dict.put("q", getQueryType());

        BEncodedValue payload = getPayload();

        if (payload != null) {
            dict.put(type.getPayloadKey(), getPayload());
        }

        ByteBuffer buffer = dict.encode();
        return Buffer.buffer(buffer.array());
    }
}
