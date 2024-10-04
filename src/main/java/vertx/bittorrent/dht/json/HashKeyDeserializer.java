package vertx.bittorrent.dht.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import vertx.bittorrent.dht.HashKey;

public class HashKeyDeserializer extends StdDeserializer<HashKey> {
    public HashKeyDeserializer() {
        super(HashKey.class);
    }

    @Override
    public HashKey deserialize(JsonParser parser, DeserializationContext context) throws IOException, JacksonException {
        return HashKey.fromHex(parser.getValueAsString());
    }
}
