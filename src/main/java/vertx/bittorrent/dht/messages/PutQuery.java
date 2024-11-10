package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;

@Getter
@Builder
public class PutQuery implements QueryPayload<PutResponse> {

    public static final String QUERY_TYPE = "put";

    private static final String FIELD_ID = "id";
    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_VALUE = "v";
    private static final String FIELD_CAS = "cas";
    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_KEY = "k";
    private static final String FIELD_SALT = "salt";
    private static final String FIELD_SIG = "sig";

    private final HashKey nodeId;
    private final byte[] token;
    private final BEncodedValue value;

    private final Long cas;
    private final Long seq;
    private final byte[] key;
    private final byte[] salt;
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

        if (cas != null) {
            dict.put(FIELD_CAS, cas);
        }

        if (seq != null) {
            dict.put(FIELD_SEQ, seq);
        }

        if (key != null) {
            dict.put(FIELD_KEY, key);
        }

        if (salt != null) {
            dict.put(FIELD_SALT, salt);
        }

        if (signature != null) {
            dict.put(FIELD_SIG, signature);
        }

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return QUERY_TYPE;
    }

    @Override
    public PutResponse parseResponse(BEncodedValue value) {
        return PutResponse.from(value);
    }

    public static PutQuery from(BEncodedValue value) {
        var dict = BEncodedDict.from(value);

        var nodeId = dict.requireBytes(FIELD_ID);
        var token = dict.requireBytes(FIELD_TOKEN);
        var v = dict.requireBEncodedValue(FIELD_VALUE);

        var cas = dict.findLong(FIELD_CAS).orElse(null);
        var seq = dict.findLong(FIELD_SEQ).orElse(null);
        var key = dict.findBytes(FIELD_KEY).orElse(null);
        var salt = dict.findBytes(FIELD_SALT).orElse(null);
        var signature = dict.findBytes(FIELD_SIG).orElse(null);

        return builder() //
                .nodeId(new HashKey(nodeId))
                .token(token)
                .value(v)
                .cas(cas)
                .seq(seq)
                .key(key)
                .salt(salt)
                .signature(signature)
                .build();
    }
}
