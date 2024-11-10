package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;

@Getter
@Builder
public class GetQuery implements QueryPayload<GetResponse> {

    public static final String QUERY_TYPE = "get";

    private static final String FIELD_ID = "id";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_SEQ = "seq";

    private final HashKey nodeId;
    private final HashKey target;
    private final Integer seq;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());
        dict.put(FIELD_TARGET, target.getBytes());

        if (seq != null) {
            dict.put(FIELD_SEQ, seq);
        }

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return QUERY_TYPE;
    }

    @Override
    public GetResponse parseResponse(BEncodedValue value) {
        return GetResponse.from(value);
    }

    public static GetQuery from(BEncodedValue value) {
        var dict = BEncodedDict.from(value);

        var nodeId = dict.requireBytes(FIELD_ID);
        var target = dict.requireBytes(FIELD_TARGET);
        var seq = dict.findInt(FIELD_SEQ).orElse(null);

        return builder() //
                .nodeId(new HashKey(nodeId))
                .target(new HashKey(target))
                .seq(seq)
                .build();
    }
}
