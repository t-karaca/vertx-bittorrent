package vertx.bittorrent.dht.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import java.io.IOException;
import vertx.bittorrent.dht.HashKey;

public class HashKeyKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext context) throws IOException {
        return HashKey.fromHex(key);
    }
}
