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

        dict.put("id", nodeId.getBytes());
        dict.put("info_hash", infoHash);

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return "get_peers";
    }

    @Override
    public GetPeersResponse parseResponse(BEncodedValue value) {
        return GetPeersResponse.from(value);
    }

    public static GetPeersQuery from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes("id");
        byte[] infoHash = dict.requireBytes("info_hash");

        return builder().nodeId(new HashKey(nodeId)).infoHash(infoHash).build();
    }
}
