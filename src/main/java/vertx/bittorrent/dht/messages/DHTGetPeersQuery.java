package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.InvalidBEncodingException;
import java.io.UncheckedIOException;
import java.util.HexFormat;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.dht.DHTNodeId;

@Getter
@SuperBuilder
public class DHTGetPeersQuery extends DHTQueryMessage<DHTGetPeersResponse> {
    private final DHTNodeId nodeId;

    private final byte[] infoHash;

    @Override
    public String toString() {
        return "DHTGetPeersQuery(nodeId=" + nodeId.toString() + ", infoHash="
                + HexFormat.of().formatHex(infoHash) + ")";
    }

    @Override
    public Class<DHTGetPeersResponse> getResponseClass() {
        return DHTGetPeersResponse.class;
    }

    @Override
    public String getQueryType() {
        return "get_peers";
    }

    @Override
    public DHTGetPeersResponse parseResponse(BEncodedValue payload) {
        return DHTGetPeersResponse.fromPayload(getTransactionId(), payload);
    }

    @Override
    public BEncodedValue getPayload() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(KEY_NODE_ID, nodeId.getBytes());
        dict.put("info_hash", infoHash);

        return dict.toValue();
    }

    public static DHTGetPeersQuery fromPayload(String transactionId, BEncodedValue payload) {
        try {
            BEncodedDict dict = new BEncodedDict(payload);

            byte[] nodeId = dict.requireBytes(KEY_NODE_ID);
            byte[] infoHash = dict.requireBytes("info_hash");

            return builder()
                    .transactionId(transactionId)
                    .nodeId(new DHTNodeId(nodeId))
                    .infoHash(infoHash)
                    .build();
        } catch (InvalidBEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
