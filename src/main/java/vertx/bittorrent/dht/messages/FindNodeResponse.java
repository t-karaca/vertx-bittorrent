package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.dht.HashKey;

@Getter
@Builder
public class FindNodeResponse implements Payload {
    private final HashKey nodeId;
    private final byte[] nodes;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put("id", nodeId.getBytes());
        dict.put("nodes", nodes);

        return dict.toValue();
    }

    public static FindNodeResponse from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes("id");
        byte[] nodes = dict.requireBytes("nodes");

        return builder().nodeId(new HashKey(nodeId)).nodes(nodes).build();
    }
}
