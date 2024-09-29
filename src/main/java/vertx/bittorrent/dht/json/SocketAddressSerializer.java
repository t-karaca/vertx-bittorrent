package vertx.bittorrent.dht.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.vertx.core.net.SocketAddress;
import java.io.IOException;

public class SocketAddressSerializer extends StdSerializer<SocketAddress> {
    public SocketAddressSerializer() {
        super(SocketAddress.class);
    }

    @Override
    public void serialize(SocketAddress address, JsonGenerator generator, SerializerProvider serializer)
            throws IOException {
        generator.writeString(address.hostAddress() + ":" + address.port());
    }
}
