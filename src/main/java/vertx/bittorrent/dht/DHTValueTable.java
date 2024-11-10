package vertx.bittorrent.dht;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.BEncoder;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import vertx.bittorrent.dht.exception.DHTErrorException;
import vertx.bittorrent.dht.messages.PutQuery;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.utils.HashUtils;

@Getter
public class DHTValueTable {
    private final Map<HashKey, DHTEntry<DHTValue>> entries;

    private final List<DHTValue> announcedValues;

    private final byte[] privateKey;
    private final byte[] publicKey;

    public DHTValueTable() {
        entries = new HashMap<>();
        announcedValues = new ArrayList<>();

        privateKey = new byte[Ed25519.SECRET_KEY_SIZE];
        publicKey = new byte[Ed25519.PUBLIC_KEY_SIZE];

        Ed25519.generatePrivateKey(new SecureRandom(), privateKey);
        Ed25519.generatePublicKey(privateKey, 0, publicKey, 0);
    }

    @JsonCreator(mode = Mode.PROPERTIES)
    public DHTValueTable(
            @JsonProperty("entries") Map<HashKey, DHTEntry<DHTValue>> entries,
            @JsonProperty("announcedValues") List<DHTValue> announcedValues,
            @JsonProperty("privateKey") byte[] privateKey,
            @JsonProperty("publicKey") byte[] publicKey) {
        this.entries = entries;
        this.announcedValues = announcedValues;

        if (privateKey == null) {
            this.privateKey = new byte[Ed25519.SECRET_KEY_SIZE];
            this.publicKey = new byte[Ed25519.PUBLIC_KEY_SIZE];

            Ed25519.generatePrivateKey(new SecureRandom(), this.privateKey);
            Ed25519.generatePublicKey(this.privateKey, 0, this.publicKey, 0);
        } else {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }

    public void put(PutQuery query) {
        if (isEmpty(query.getKey())) {
            byte[] bytes = encode(query.getValue());
            HashKey key = new HashKey(HashUtils.sha1(bytes));

            var entry = entries.computeIfAbsent(
                    key,
                    k -> new DHTEntry<>(
                            new DHTValue().setValue(query.getValue()).setHashKey(k)));
            entry.refresh();
        } else {
            boolean hasSalt = !isEmpty(query.getSalt());
            if (hasSalt && query.getSalt().length > 64) {
                throw DHTErrorException.create(207, "salt too big");
            }

            byte[] payload = makeSignaturePayload(query.getSalt(), query.getSeq(), query.getValue())
                    .getBytes(StandardCharsets.UTF_8);

            boolean isSignatureValid =
                    Ed25519.verify(query.getSignature(), 0, query.getKey(), 0, payload, 0, payload.length);

            if (!isSignatureValid) {
                throw DHTErrorException.create(206, "Invalid signature");
            }

            HashKey key = makeKeyForMutable(query.getSalt(), query.getKey());
            var entry = entries.get(key);

            if (entry != null && entry.isStale()) {
                entries.remove(key);
                entry = null;
            }

            long seq = query.getSeq() != null ? query.getSeq() : 0;

            if (entry == null) {
                entry = new DHTEntry<>(new DHTValue()
                        .setHashKey(key)
                        .setValue(query.getValue())
                        .setSignature(query.getSignature())
                        .setKey(query.getKey())
                        .setSalt(query.getSalt())
                        .setSequenceNumber(seq));

                entries.put(key, entry);
            } else {
                var value = entry.getValue();

                if (value.getSequenceNumber() > seq) {
                    throw DHTErrorException.create(302, "sequence number less than current");
                }

                if (query.getCas() != null && value.getSequenceNumber() != query.getCas()) {
                    throw DHTErrorException.create(301, "the CAS hash mismatched, re-read value and try again.");
                }

                value.setSequenceNumber(seq);
                value.setValue(query.getValue());
                value.setSignature(query.getSignature());

                entry.refresh();
            }
        }
    }

    public Optional<DHTValue> find(HashKey key) {
        var entry = entries.get(key);

        if (entry != null) {
            if (entry.isStale()) {
                entries.remove(key);
            } else {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    public Optional<DHTValue> findMutable(byte[] salt) {
        return announcedValues.stream()
                .filter(v -> Arrays.equals(v.getSalt(), salt))
                .findAny();
    }

    public DHTValue makeImmutable(BEncodedValue value) {
        byte[] bytes = encode(value);
        HashKey key = new HashKey(HashUtils.sha1(bytes));

        var optional =
                announcedValues.stream().filter(v -> v.getHashKey().equals(key)).findAny();
        if (optional.isPresent()) {
            return optional.get();
        }

        var dhtValue = new DHTValue().setValue(value).setHashKey(key);
        announcedValues.add(dhtValue);
        return dhtValue;
    }

    public DHTValue makeMutable(byte[] salt, BEncodedValue value) {
        // byte[] salt = new byte[32];
        //
        // do {
        //     RandomUtils.randomBytes(salt);
        // } while (announcedValues.stream().noneMatch(v -> Arrays.equals(v.getSalt(), salt)));

        if (findMutable(salt).isPresent()) {
            throw new IllegalStateException("entry with salt already exists");
        }

        var hashKey = makeKeyForMutable(salt, publicKey);

        var dhtValue = new DHTValue()
                .setSalt(salt)
                .setValue(value)
                .setHashKey(hashKey)
                .setKey(publicKey)
                .setSequenceNumber(1);

        signValue(dhtValue);

        announcedValues.add(dhtValue);
        return dhtValue;
    }

    public void signValue(DHTValue value) {
        if (!Arrays.equals(value.getKey(), publicKey)) {
            throw new IllegalArgumentException("value was not created by this value table");
        }

        byte[] signature = new byte[Ed25519.SIGNATURE_SIZE];
        byte[] payload = makeSignaturePayload(value.getSalt(), value.getSequenceNumber(), value.getValue())
                .getBytes(StandardCharsets.UTF_8);

        Ed25519.sign(privateKey, 0, payload, 0, payload.length, signature, 0);

        value.setSignature(signature);
    }

    public static HashKey makeKeyForMutable(byte[] salt, byte[] publicKey) {
        boolean hasSalt = !isEmpty(salt);

        int length = publicKey.length;
        if (hasSalt) {
            length += salt.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.put(publicKey);
        if (hasSalt) {
            buffer.put(salt);
        }

        return new HashKey(HashUtils.sha1(buffer.array()));
    }

    public static String makeSignaturePayload(byte[] salt, long seq, BEncodedValue value) {
        StringBuilder builder = new StringBuilder();

        if (salt != null) {
            String saltString = new String(salt, StandardCharsets.UTF_8);

            builder.append("4:salt");
            builder.append(saltString.length());
            builder.append(':');
            builder.append(saltString);
        }

        builder.append("3:seqi");
        builder.append(seq);
        builder.append("e1:v");

        try (var baos = new ByteArrayOutputStream()) {
            BEncoder.encode(value, baos);

            byte[] bytes = baos.toByteArray();

            builder.append(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return builder.toString();
    }

    private static byte[] encode(BEncodedValue value) {
        try (var os = new ByteArrayOutputStream()) {
            BEncoder.encode(value, os);
            return os.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }
}
