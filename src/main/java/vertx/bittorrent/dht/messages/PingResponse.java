package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Getter;
import lombok.ToString;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;

@Getter
@ToString
public class PingResponse implements Payload {
    private static final String FIELD_ID = "id";

    private final HashKey nodeId;

    public PingResponse(HashKey nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());

        return dict.toValue();
    }

    public static PingResponse from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);

        return new PingResponse(new HashKey(nodeId));
    }
}
