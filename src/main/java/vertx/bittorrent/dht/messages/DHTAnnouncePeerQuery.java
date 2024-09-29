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
public class DHTAnnouncePeerQuery extends DHTQueryMessage<DHTAnnouncePeerResponse> {
    private final DHTNodeId nodeId;

    private final boolean impliedPort;
    private final byte[] infoHash;
    private final int port;
    private final byte[] token;

    @Override
    public String toString() {
        return "DHTAnnouncePeerQuery(nodeId=" + nodeId.toString() + ", impliedPort=" + impliedPort + ", infoHash="
                + HexFormat.of().formatHex(infoHash) + ", port=" + port + ", token="
                + HexFormat.of().formatHex(token) + ")";
    }

    @Override
    public Class<DHTAnnouncePeerResponse> getResponseClass() {
        return DHTAnnouncePeerResponse.class;
    }

    @Override
    public String getQueryType() {
        return "announce_peer";
    }

    @Override
    public DHTAnnouncePeerResponse parseResponse(BEncodedValue payload) {
        return DHTAnnouncePeerResponse.fromPayload(getTransactionId(), payload);
    }

    @Override
    public BEncodedValue getPayload() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(KEY_NODE_ID, nodeId.getBytes());
        dict.put("implied_port", impliedPort ? 1 : 0);
        dict.put("info_hash", infoHash);
        dict.put("port", port);
        dict.put("token", token);

        return dict.toValue();
    }

    public static DHTAnnouncePeerQuery fromPayload(String transactionId, BEncodedValue payload) {
        try {
            BEncodedDict dict = new BEncodedDict(payload);

            byte[] nodeId = dict.requireBytes(KEY_NODE_ID);
            boolean impliedPort = dict.findInt("implied_port").map(v -> v == 1).orElse(false);
            byte[] infoHash = dict.requireBytes("info_hash");
            int port = dict.findInt("port").orElse(-1);
            byte[] token = dict.requireBytes("token");

            return builder()
                    .transactionId(transactionId)
                    .nodeId(new DHTNodeId(nodeId))
                    .impliedPort(impliedPort)
                    .infoHash(infoHash)
                    .port(port)
                    .token(token)
                    .build();
        } catch (InvalidBEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
