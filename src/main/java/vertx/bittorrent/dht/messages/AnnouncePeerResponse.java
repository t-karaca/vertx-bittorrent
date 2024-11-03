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
public class AnnouncePeerResponse implements Payload {

    private static final String FIELD_ID = "id";

    private final HashKey nodeId;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());

        return dict.toValue();
    }

    public static AnnouncePeerResponse from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);

        return builder().nodeId(new HashKey(nodeId)).build();
    }
}
