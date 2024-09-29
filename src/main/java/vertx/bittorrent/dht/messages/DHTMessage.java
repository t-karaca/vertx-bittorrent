package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import io.vertx.core.buffer.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vertx.bittorrent.BEncodedDict;

@Getter
@RequiredArgsConstructor
@SuperBuilder
public abstract class DHTMessage {

    public static final String KEY_TRANSACTION_ID = "t";
    public static final String KEY_MESSAGE_TYPE = "y";
    public static final String KEY_NODE_ID = "id";

    public enum Type {
        QUERY("q", "a"),
        RESPONSE("r", "r"),
        ERROR("e", "e");

        private String key;
        private String payloadKey;

        Type(String key, String payloadKey) {
            this.key = key;
            this.payloadKey = payloadKey;
        }

        public String getKey() {
            return this.key;
        }

        public String getPayloadKey() {
            return this.payloadKey;
        }

        public static Type fromKey(String key) {
            for (var type : Type.values()) {
                if (type.getKey().equals(key)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Could not resolve type for key '" + key + "'");
        }
    }

    @Setter
    private String transactionId;

    public Buffer toBuffer() {
        BEncodedDict dict = new BEncodedDict();

        Type type = getMessageType();

        dict.put(KEY_TRANSACTION_ID, transactionId);
        dict.put(KEY_MESSAGE_TYPE, type.getKey());

        BEncodedValue payload = getPayload();

        if (payload != null) {
            dict.put(type.getPayloadKey(), getPayload());
        }

        ByteBuffer buffer = dict.encode();
        return Buffer.buffer(buffer.array());
    }

    public Type getMessageType() {
        return Type.QUERY;
    }

    public BEncodedValue getPayload() {
        return null;
    }
}
