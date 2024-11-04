package vertx.bittorrent.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.vertx.core.net.SocketAddress;
import java.io.IOException;

public class SocketAddressDeserializer extends StdDeserializer<SocketAddress> {
    public SocketAddressDeserializer() {
        super(SocketAddress.class);
    }

    @Override
    public SocketAddress deserialize(JsonParser parser, DeserializationContext context)
            throws IOException, JacksonException {
        String addressString = parser.getValueAsString();

        String[] parts = addressString.split(":");

        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        return SocketAddress.inetSocketAddress(port, host);
    }
}
