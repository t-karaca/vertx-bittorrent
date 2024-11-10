package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;

@Getter
@Builder
public class PutResponse implements Payload {
    private static final String FIELD_ID = "id";
    private final HashKey nodeId;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());

        return dict.toValue();
    }

    public static PutResponse from(BEncodedValue value) {
        var dict = BEncodedDict.from(value);

        var nodeId = dict.requireBytes(FIELD_ID);

        return builder().nodeId(new HashKey(nodeId)).build();
    }
}
