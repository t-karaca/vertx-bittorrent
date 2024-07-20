package vertx.bittorrent;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.InvalidBEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;

@Getter
public class BEncodedDict {

    private final Map<String, BEncodedValue> map;

    public BEncodedDict(BEncodedValue value) throws InvalidBEncodingException {
        this.map = value.getMap();
    }

    public Optional<Object> findValue(String key) {
        return Optional.ofNullable(map.get(key)).map(BEncodedValue::getValue);
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
