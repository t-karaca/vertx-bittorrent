package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.ToStringBuilder;
import vertx.bittorrent.dht.HashKey;

@Getter
@Builder
public class GetPeersQuery implements QueryPayload<GetPeersResponse> {

    public static final String QUERY_TYPE = "get_peers";

    private static final String FIELD_ID = "id";
    private static final String FIELD_INFO_HASH = "info_hash";

    private final HashKey nodeId;
    private final byte[] infoHash;

    @Override
    public String toString() {
        return ToStringBuilder.builder(getClass())
                .field("nodeId", nodeId)
                .field("infoHash", infoHash)
                .build();
    }

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());
        dict.put(FIELD_INFO_HASH, infoHash);

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return QUERY_TYPE;
    }

    @Override
    public GetPeersResponse parseResponse(BEncodedValue value) {
        return GetPeersResponse.from(value);
    }

    public static GetPeersQuery from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);
        byte[] infoHash = dict.requireBytes(FIELD_INFO_HASH);

        return builder().nodeId(new HashKey(nodeId)).infoHash(infoHash).build();
    }
}
