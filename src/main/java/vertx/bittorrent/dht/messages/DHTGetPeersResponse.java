package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.InvalidBEncodingException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.Peer;
import vertx.bittorrent.TrackerResponse;
import vertx.bittorrent.dht.DHTNode;
import vertx.bittorrent.dht.DHTNodeId;

@Getter
@SuperBuilder
public class DHTGetPeersResponse extends DHTMessage {
    private final DHTNodeId nodeId;

    private final byte[] token;

    private final List<byte[]> values;
    private final byte[] nodes;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("DHTGetPeersResponse(");

        builder.append("nodeId=");
        builder.append(nodeId.toString());
        builder.append(", token=");

        if (token != null) {
            builder.append(HexFormat.of().formatHex(token));
        } else {
            builder.append("null");
        }

        builder.append(", values=");

        if (values != null) {
            builder.append("[");
            boolean addComma = false;
            for (var v : values) {
                Collection<Peer> peers = TrackerResponse.parsePeers4(v);
                for (var p : peers) {
                    if (addComma) {
                        builder.append(", ");
                    }

                    builder.append(p.toString());
                    addComma = true;
                }
            }
            builder.append("]");
        } else {
            builder.append("null");
        }

        builder.append(", nodes=");
        if (nodes != null) {
            ByteBuffer buffer = ByteBuffer.wrap(nodes);

            builder.append("[");

            boolean addComma = false;
            DHTNode n;
            while ((n = DHTNode.fromCompact(buffer)) != null) {
                if (addComma) {
                    builder.append(", ");
                }

                builder.append(n.toString());
                addComma = true;
            }

            builder.append("]");
        } else {
            builder.append("null");
        }

        builder.append(")");

        return builder.toString();
    }

    @Override
    public Type getMessageType() {
        return Type.RESPONSE;
    }

    @Override
    public BEncodedValue getPayload() {
        BEncodedDict dict = new BEncodedDict();

        dict.put(KEY_NODE_ID, nodeId.getBytes());
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

    public static DHTGetPeersResponse fromPayload(String transactionId, BEncodedValue payload) {
        try {
            BEncodedDict dict = new BEncodedDict(payload);

            byte[] nodeId = dict.requireBytes(KEY_NODE_ID);
            byte[] token = dict.findBytes("token").orElse(null);

            List<byte[]> peers = dict.findList("values").stream()
                    .flatMap(v -> v.stream())
                    .map(v -> (byte[]) v.getValue())
                    .toList();

            byte[] nodes = dict.findBytes("nodes").orElseGet(() -> new byte[0]);

            return builder()
                    .transactionId(transactionId)
                    .nodeId(new DHTNodeId(nodeId))
                    .token(token)
                    .values(peers)
                    .nodes(nodes)
                    .build();
        } catch (InvalidBEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
