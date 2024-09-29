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
public class DHTPingQuery extends DHTQueryMessage<DHTPingResponse> {
    private final DHTNodeId nodeId;

    @Override
    public Class<DHTPingResponse> getResponseClass() {
        return DHTPingResponse.class;
    }

    @Override
    public String getQueryType() {
        return "ping";
    }

    @Override
    public DHTPingResponse parseResponse(BEncodedValue payload) {
        return DHTPingResponse.fromPayload(getTransactionId(), payload);
    }

    @Override
    public BEncodedValue getPayload() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(KEY_NODE_ID, nodeId.getBytes());

        return dict.toValue();
    }

    public static DHTPingQuery fromPayload(String transactionId, BEncodedValue payload) {
        try {
            BEncodedDict dict = new BEncodedDict(payload);

            byte[] nodeId = dict.requireBytes(KEY_NODE_ID);

            return builder()
                    .transactionId(transactionId)
                    .nodeId(new DHTNodeId(nodeId))
                    .build();
        } catch (InvalidBEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
