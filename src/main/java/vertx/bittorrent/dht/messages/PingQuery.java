package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Getter;
import lombok.ToString;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;

@Getter
@ToString
public class PingQuery implements QueryPayload<PingResponse> {

    public static final String QUERY_TYPE = "ping";

    private static final String FIELD_ID = "id";

    private final HashKey nodeId;

    public PingQuery(HashKey nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public PingResponse parseResponse(BEncodedValue value) {
        return PingResponse.from(value);
    }

    @Override
    public String queryType() {
        return QUERY_TYPE;
    }

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());

        return dict.toValue();
    }

    public static PingQuery from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);

        return new PingQuery(new HashKey(nodeId));
    }
}
