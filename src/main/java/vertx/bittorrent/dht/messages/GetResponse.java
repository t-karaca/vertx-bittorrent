package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;

@Getter
@Builder
public class GetResponse implements Payload {

    private static final String FIELD_ID = "id";
    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_VALUE = "v";
    private static final String FIELD_NODES = "nodes";
    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_KEY = "k";
    private static final String FIELD_SIG = "sig";

    private final HashKey nodeId;
    private final byte[] token;
    private final BEncodedValue value;
    private final byte[] nodes;

    private final Long seq;
    private final byte[] key;
    private final byte[] signature;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());

        if (token != null) {
            dict.put(FIELD_TOKEN, token);
        }

        if (value != null) {
            dict.put(FIELD_VALUE, value);
        }

        if (nodes != null) {
            dict.put(FIELD_NODES, nodes);
        }

        if (seq != null) {
            dict.put(FIELD_SEQ, seq);
        }

        if (key != null) {
            dict.put(FIELD_KEY, key);
        }

        if (signature != null) {
            dict.put(FIELD_SIG, signature);
        }

        return dict.toValue();
    }

    public static GetResponse from(BEncodedValue value) {
        var dict = BEncodedDict.from(value);

        var nodeId = dict.requireBytes(FIELD_ID);
        var token = dict.findBytes(FIELD_TOKEN).orElse(null);
        var v = dict.findBEncodedValue(FIELD_VALUE).orElse(null);
        var nodes = dict.findBytes(FIELD_NODES).orElse(null);
        var seq = dict.findLong(FIELD_SEQ).orElse(null);
        var key = dict.findBytes(FIELD_KEY).orElse(null);
        var signature = dict.findBytes(FIELD_SIG).orElse(null);

        return builder()
                .nodeId(new HashKey(nodeId))
                .token(token)
                .value(v)
                .nodes(nodes)
                .seq(seq)
                .key(key)
                .signature(signature)
                .build();
    }
}
