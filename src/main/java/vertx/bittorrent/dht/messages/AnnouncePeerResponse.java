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
    private final HashKey nodeId;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put("id", nodeId.getBytes());

        return dict.toValue();
    }

    public static AnnouncePeerResponse from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes("id");

        return builder().nodeId(new HashKey(nodeId)).build();
    }
}
