package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.InvalidBEncodingException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import vertx.bittorrent.BEncodedDict;
import vertx.bittorrent.dht.DHTNode;
import vertx.bittorrent.dht.DHTNodeId;

@Getter
@SuperBuilder
public class DHTFindNodeResponse extends DHTMessage {
    private final DHTNodeId nodeId;

    private final byte[] nodes;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("DHTGetPeersResponse(");

        builder.append("nodeId=");
        builder.append(nodeId.toString());

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
        dict.put("nodes", nodes);

        return dict.toValue();
    }

    public static DHTFindNodeResponse fromPayload(String transactionId, BEncodedValue payload) {
        try {
            BEncodedDict dict = new BEncodedDict(payload);

            byte[] nodeId = dict.requireBytes(KEY_NODE_ID);
            byte[] nodes = dict.requireBytes("nodes");

            return builder()
                    .transactionId(transactionId)
                    .nodeId(new DHTNodeId(nodeId))
                    .nodes(nodes)
                    .build();
        } catch (InvalidBEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
