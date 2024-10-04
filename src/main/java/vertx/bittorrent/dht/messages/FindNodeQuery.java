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
    private final HashKey nodeId;
    private final HashKey target;

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put("id", nodeId.getBytes());
        dict.put("target", target.getBytes());

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return "find_node";
    }

    @Override
    public FindNodeResponse parseResponse(BEncodedValue value) {
        return FindNodeResponse.from(value);
    }

    public static FindNodeQuery from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes("id");
        byte[] target = dict.requireBytes("target");

        return builder().nodeId(new HashKey(nodeId)).target(new HashKey(target)).build();
    }
}
