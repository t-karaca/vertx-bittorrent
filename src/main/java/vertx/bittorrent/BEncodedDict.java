package vertx.bittorrent;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.BEncoder;
import be.adaxisoft.bencode.InvalidBEncodingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;

@Getter
public class BEncodedDict {

    private final Map<String, BEncodedValue> map;

    public BEncodedDict() {
        this.map = new HashMap<>();
    }

    public BEncodedDict(BEncodedValue value) throws InvalidBEncodingException {
        this.map = value.getMap();
    }

    public void put(String key, String value) {
        try {
            map.put(key, new BEncodedValue(value));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void put(String key, int value) {
        map.put(key, new BEncodedValue(value));
    }

    public void put(String key, long value) {
        map.put(key, new BEncodedValue(value));
    }

    public void put(String key, byte[] value) {
        map.put(key, new BEncodedValue(value));
    }

    public void put(String key, BEncodedValue value) {
        map.put(key, value);
    }

    public void put(String key, List<BEncodedValue> value) {
        map.put(key, new BEncodedValue(value));
    }

    public Optional<Object> findValue(String key) {
        return Optional.ofNullable(map.get(key)).map(BEncodedValue::getValue);
    }

    public Optional<BEncodedValue> findBEncodedValue(String key) {
        return Optional.ofNullable(map.get(key));
    }

    public Optional<String> findString(String key) {
        return Optional.ofNullable(map.get(key)).map(value -> {
            try {
                return value.getString();
            } catch (InvalidBEncodingException e) {
                throw new IllegalArgumentException("Field '" + key + "' is not a string");
            }
        });
    }

    public Optional<Integer> findInt(String key) {
        return Optional.ofNullable(map.get(key)).map(value -> {
            try {
                return value.getInt();
            } catch (InvalidBEncodingException e) {
                throw new IllegalArgumentException("Field '" + key + "' is not a long");
            }
        });
    }

    public Optional<Long> findLong(String key) {
        return Optional.ofNullable(map.get(key)).map(value -> {
            try {
                return value.getLong();
            } catch (InvalidBEncodingException e) {
                throw new IllegalArgumentException("Field '" + key + "' is not a long");
            }
        });
    }

    public Optional<List<BEncodedValue>> findList(String key) {
        return Optional.ofNullable(map.get(key)).map(value -> {
            try {
                return value.getList();
            } catch (InvalidBEncodingException e) {
                throw new IllegalArgumentException("Field '" + key + "' is not a dict");
            }
        });
    }

    public Optional<BEncodedDict> findDict(String key) {
        return Optional.ofNullable(map.get(key)).map(value -> {
            try {
                return new BEncodedDict(value);
            } catch (InvalidBEncodingException e) {
                throw new IllegalArgumentException("Field '" + key + "' is not a dict");
            }
        });
    }

    public Optional<byte[]> findBytes(String key) {
        return Optional.ofNullable(map.get(key)).map(value -> {
            try {
                return value.getBytes();
            } catch (InvalidBEncodingException e) {
                throw new IllegalArgumentException("Field '" + key + "' is not a byte array");
            }
        });
    }

    public BEncodedValue requireBEncodedValue(String key) {
        return findBEncodedValue(key).orElseThrow(() -> missingFieldException(key));
    }

    public String requireString(String key) {
        return findString(key).orElseThrow(() -> missingFieldException(key));
    }

    public int requireInt(String key) {
        return findInt(key).orElseThrow(() -> missingFieldException(key));
    }

    public long requireLong(String key) {
        return findLong(key).orElseThrow(() -> missingFieldException(key));
    }

    public List<BEncodedValue> requireList(String key) {
        return findList(key).orElseThrow(() -> missingFieldException(key));
    }

    public BEncodedDict requireDict(String key) {
        return findDict(key).orElseThrow(() -> missingFieldException(key));
    }

    public byte[] requireBytes(String key) {
        return findBytes(key).orElseThrow(() -> missingFieldException(key));
    }

    public ByteBuffer encode() {
        try {
            return BEncoder.encode(map);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public BEncodedValue toValue() {
        return new BEncodedValue(map);
    }

    private IllegalArgumentException missingFieldException(String key) {
        return new IllegalArgumentException("Could not find field '" + key + "'");
    }

    public static BEncodedDict from(BEncodedValue value) {
        try {
            return new BEncodedDict(value);
        } catch (InvalidBEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
