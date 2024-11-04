package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.utils.ToStringBuilder;

@Getter
@Builder
public class GetPeersResponse implements Payload {

    private static final String FIELD_ID = "id";
    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_VALUES = "values";
    private static final String FIELD_NODES = "nodes";

    private final HashKey nodeId;
    private final byte[] token;
    private final List<byte[]> values;
    private final byte[] nodes;

    @Override
    public String toString() {
        return ToStringBuilder.builder(getClass())
                .field("nodeId", nodeId)
                .field(FIELD_TOKEN, token)
                .build();
    }

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());
        dict.put(FIELD_TOKEN, token);

        if (values != null && !values.isEmpty()) {
            var list = values.stream().map(v -> new BEncodedValue(v)).toList();

            dict.put(FIELD_VALUES, list);
        }

        if (nodes != null) {
            dict.put(FIELD_NODES, nodes);
        }

        return dict.toValue();
    }

    public static GetPeersResponse from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);
        byte[] token = dict.findBytes(FIELD_TOKEN).orElse(null);

        List<byte[]> peers = dict.findList(FIELD_VALUES).stream()
                .flatMap(v -> v.stream())
                .map(v -> (byte[]) v.getValue())
                .toList();

        byte[] nodes = dict.findBytes(FIELD_NODES).orElseGet(() -> new byte[0]);

        return builder()
                .nodeId(new HashKey(nodeId))
                .token(token)
                .values(peers)
                .nodes(nodes)
                .build();
    }
}
