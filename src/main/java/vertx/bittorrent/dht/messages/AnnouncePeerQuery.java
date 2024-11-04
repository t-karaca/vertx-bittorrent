package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.utils.ToStringBuilder;

@Getter
@Builder
public class AnnouncePeerQuery implements QueryPayload<AnnouncePeerResponse> {

    public static final String QUERY_TYPE = "announce_peer";

    private static final String FIELD_ID = "id";
    private static final String FIELD_IMPLIED_PORT = "implied_port";
    private static final String FIELD_INFO_HASH = "info_hash";
    private static final String FIELD_PORT = "port";
    private static final String FIELD_TOKEN = "token";

    private final HashKey nodeId;
    private final boolean impliedPort;
    private final byte[] infoHash;
    private final int port;
    private final byte[] token;

    @Override
    public String toString() {
        return ToStringBuilder.builder(getClass())
                .field("nodeId", nodeId)
                .field("impliedPort", impliedPort)
                .field("infoHash", infoHash)
                .field("port", port)
                .field("token", token)
                .build();
    }

    @Override
    public BEncodedValue value() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(FIELD_ID, nodeId.getBytes());
        dict.put(FIELD_IMPLIED_PORT, impliedPort ? 1 : 0);
        dict.put(FIELD_INFO_HASH, infoHash);
        dict.put(FIELD_PORT, port);
        dict.put(FIELD_TOKEN, token);

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return QUERY_TYPE;
    }

    @Override
    public AnnouncePeerResponse parseResponse(BEncodedValue value) {
        return AnnouncePeerResponse.from(value);
    }

    public static AnnouncePeerQuery from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes(FIELD_ID);
        boolean impliedPort = dict.findInt(FIELD_IMPLIED_PORT).map(v -> v == 1).orElse(false);
        byte[] infoHash = dict.requireBytes(FIELD_INFO_HASH);
        int port = dict.findInt(FIELD_PORT).orElse(-1);
        byte[] token = dict.requireBytes(FIELD_TOKEN);

        return builder()
                .nodeId(new HashKey(nodeId))
                .impliedPort(impliedPort)
                .infoHash(infoHash)
                .port(port)
                .token(token)
                .build();
    }
}
