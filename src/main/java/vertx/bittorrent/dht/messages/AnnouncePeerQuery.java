package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import lombok.Builder;
import lombok.Getter;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.ToStringBuilder;
import vertx.bittorrent.dht.HashKey;

@Getter
@Builder
public class AnnouncePeerQuery implements QueryPayload<AnnouncePeerResponse> {
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

        dict.put("id", nodeId.getBytes());
        dict.put("implied_port", impliedPort ? 1 : 0);
        dict.put("info_hash", infoHash);
        dict.put("port", port);
        dict.put("token", token);

        return dict.toValue();
    }

    @Override
    public String queryType() {
        return "announce_peer";
    }

    @Override
    public AnnouncePeerResponse parseResponse(BEncodedValue value) {
        return AnnouncePeerResponse.from(value);
    }

    public static AnnouncePeerQuery from(BEncodedValue value) {
        BEncodedDict dict = BEncodedDict.from(value);

        byte[] nodeId = dict.requireBytes("id");
        boolean impliedPort = dict.findInt("implied_port").map(v -> v == 1).orElse(false);
        byte[] infoHash = dict.requireBytes("info_hash");
        int port = dict.findInt("port").orElse(-1);
        byte[] token = dict.requireBytes("token");

        return builder()
                .nodeId(new HashKey(nodeId))
                .impliedPort(impliedPort)
                .infoHash(infoHash)
                .port(port)
                .token(token)
                .build();
    }
}
