package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.InvalidBEncodingException;
import java.io.UncheckedIOException;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.dht.DHTNodeId;

@Getter
@SuperBuilder
@ToString
public class DHTFindNodeQuery extends DHTQueryMessage<DHTFindNodeResponse> {
    private final DHTNodeId nodeId;

    private final DHTNodeId target;

    @Override
    public String getQueryType() {
        return "find_node";
    }

    @Override
    public Class<DHTFindNodeResponse> getResponseClass() {
        return DHTFindNodeResponse.class;
    }

    @Override
    public DHTFindNodeResponse parseResponse(BEncodedValue payload) {
        return DHTFindNodeResponse.fromPayload(getTransactionId(), payload);
    }

    @Override
    public BEncodedValue getPayload() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(KEY_NODE_ID, nodeId.getBytes());
        dict.put("target", target.getBytes());

        return dict.toValue();
    }

    public static DHTFindNodeQuery fromPayload(String transactionId, BEncodedValue payload) {
        try {
            BEncodedDict dict = new BEncodedDict(payload);

            byte[] nodeId = dict.requireBytes(KEY_NODE_ID);
            byte[] target = dict.requireBytes("target");

            return builder()
                    .transactionId(transactionId)
                    .nodeId(new DHTNodeId(nodeId))
                    .target(new DHTNodeId(target))
                    .build();
        } catch (InvalidBEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
