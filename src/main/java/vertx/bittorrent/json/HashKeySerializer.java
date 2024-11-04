package vertx.bittorrent.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import vertx.bittorrent.model.HashKey;

public class HashKeySerializer extends StdSerializer<HashKey> {
    public HashKeySerializer() {
        super(HashKey.class);
    }

    @Override
    public void serialize(HashKey nodeId, JsonGenerator generator, SerializerProvider serializer) throws IOException {
        generator.writeString(nodeId.toString());
    }
}
