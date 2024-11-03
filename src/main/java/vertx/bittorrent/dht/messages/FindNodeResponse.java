package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.ToStringBuilder;
import vertx.bittorrent.dht.HashKey;

@Getter
@Builder
public class FindNodeResponse implements Payload {
    private static final String FIELD_ID = "id";
    private static final String FIELD_NODES = "nodes";

    private final HashKey nodeId;
    private final byte[] nodes;

    @Override
    public String toString() {
        return ToStringBuilder.builder(getClass())
                .field("nodeId", nodeId)
                .field("nodes.length", nodes.length)
                .build();
    }

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());
        dict.put(FIELD_NODES, nodes);

        return dict.toValue();
    }

    public static FindNodeResponse from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);
        byte[] nodes = dict.requireBytes(FIELD_NODES);

        return builder().nodeId(new HashKey(nodeId)).nodes(nodes).build();
    }
}
