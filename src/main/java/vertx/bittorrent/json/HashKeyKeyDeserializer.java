package vertx.bittorrent.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import java.io.IOException;
import vertx.bittorrent.model.HashKey;

public class HashKeyKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext context) throws IOException {
        return HashKey.fromHex(key);
    }
}
