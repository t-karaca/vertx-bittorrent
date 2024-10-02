package vertx.bittorrent.dht.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import vertx.bittorrent.dht.DHTNodeId;

public class DHTNodeIdSerializer extends StdSerializer<DHTNodeId> {
    public DHTNodeIdSerializer() {
        super(DHTNodeId.class);
    }

    @Override
    public void serialize(DHTNodeId nodeId, JsonGenerator generator, SerializerProvider serializer) throws IOException {
        generator.writeString(nodeId.toString());
    }
}
