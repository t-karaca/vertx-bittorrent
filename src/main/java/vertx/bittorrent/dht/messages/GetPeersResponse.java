package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.ToStringBuilder;
import vertx.bittorrent.dht.HashKey;

@Getter
@Builder
public class GetPeersResponse implements Payload {
    private final HashKey nodeId;
    private final byte[] token;
    private final List<byte[]> values;
    private final byte[] nodes;

    @Override
    public String toString() {
        return ToStringBuilder.builder(getClass())
                .field("nodeId", nodeId)
                .field("token", token)
                .build();
    }

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put("id", nodeId.getBytes());
        dict.put("token", token);

        if (values != null && !values.isEmpty()) {
            var list = values.stream().map(v -> new BEncodedValue(v)).toList();

            dict.put("values", list);
        }

        if (nodes != null) {
            dict.put("nodes", nodes);
        }

        return dict.toValue();
    }

    public static GetPeersResponse from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes("id");
        byte[] token = dict.findBytes("token").orElse(null);

        List<byte[]> peers = dict.findList("values").stream()
                .flatMap(v -> v.stream())
                .map(v -> (byte[]) v.getValue())
                .toList();

        byte[] nodes = dict.findBytes("nodes").orElseGet(() -> new byte[0]);

        return builder()
                .nodeId(new HashKey(nodeId))
                .token(token)
                .values(peers)
                .nodes(nodes)
                .build();
    }
}
