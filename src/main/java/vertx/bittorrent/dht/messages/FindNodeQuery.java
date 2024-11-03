package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.dht.HashKey;

@Getter
@Builder
@ToString
public class FindNodeQuery implements QueryPayload<FindNodeResponse> {

    public static final String QUERY_TYPE = "find_node";

    private static final String FIELD_ID = "id";
    private static final String FIELD_TARGET = "target";

    private final HashKey nodeId;
    private final HashKey target;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());
        dict.put(FIELD_TARGET, target.getBytes());

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return QUERY_TYPE;
    }

    @Override
    public FindNodeResponse parseResponse(BEncodedValue value) {
        return FindNodeResponse.from(value);
    }

    public static FindNodeQuery from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);
        byte[] target = dict.requireBytes(FIELD_TARGET);

        return builder().nodeId(new HashKey(nodeId)).target(new HashKey(target)).build();
    }
}
