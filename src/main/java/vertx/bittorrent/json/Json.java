package vertx.bittorrent.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.net.SocketAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import vertx.bittorrent.model.HashKey;

public final class Json {

    public static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private Json() {}

    public static String write(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeTo(OutputStream outputStream, Object object) throws IOException {
        OBJECT_MAPPER.writeValue(outputStream, object);
    }

    public static String pretty(Object object) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void prettyTo(OutputStream outputStream, Object object) throws IOException {
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputStream, object);
    }

    public static <T> T read(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T readFrom(InputStream inputStream, Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.readValue(inputStream, clazz);
    }

    private static ObjectMapper createObjectMapper() {
        JsonFactory jsonFactory = new MappingJsonFactory();
        jsonFactory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        SimpleModule module = new SimpleModule();

        module.addSerializer(HashKey.class, new HashKeySerializer());
        module.addDeserializer(HashKey.class, new HashKeyDeserializer());

        module.addSerializer(SocketAddress.class, new SocketAddressSerializer());
        module.addDeserializer(SocketAddress.class, new SocketAddressDeserializer());

        module.addKeyDeserializer(HashKey.class, new HashKeyKeyDeserializer());

        return new ObjectMapper(jsonFactory)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                .registerModule(module)
                .registerModule(new JavaTimeModule());
    }
}
