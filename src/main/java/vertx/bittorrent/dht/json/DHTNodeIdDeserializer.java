package vertx.bittorrent.dht.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import vertx.bittorrent.dht.DHTNodeId;

public class DHTNodeIdDeserializer extends StdDeserializer<DHTNodeId> {
    public DHTNodeIdDeserializer() {
        super(DHTNodeId.class);
    }

    @Override
    public DHTNodeId deserialize(JsonParser parser, DeserializationContext context)
            throws IOException, JacksonException {
        return DHTNodeId.fromHex(parser.getValueAsString());
    }
}
