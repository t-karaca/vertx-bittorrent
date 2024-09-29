package vertx.bittorrent.dht.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import java.io.IOException;
import vertx.bittorrent.dht.DHTNodeId;

public class DHTNodeIdKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext context) throws IOException {
        return DHTNodeId.fromHex(key);
    }
}
