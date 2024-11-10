package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import be.adaxisoft.bencode.BDecoder;
import be.adaxisoft.bencode.BEncodedValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.dht.DHTValueTable;
import vertx.bittorrent.dht.exception.DHTErrorException;
import vertx.bittorrent.dht.messages.PutQuery;
import vertx.bittorrent.model.HashKey;
import vertx.bittorrent.utils.HashUtils;

public class DHTValueTest {
    @Test
    @DisplayName("should make signature payload")
    void testSignaturePayload() {
        assertThat(DHTValueTable.makeSignaturePayload(null, 1, toValue("12:Hello World!")))
                .isEqualTo("3:seqi1e1:v12:Hello World!");

        assertThat(DHTValueTable.makeSignaturePayload(
                        "foobar".getBytes(StandardCharsets.UTF_8), 1, toValue("12:Hello World!")))
                .isEqualTo("4:salt6:foobar3:seqi1e1:v12:Hello World!");
    }

    @Test
    @DisplayName("should put immutable data")
    void testImmutable() throws Exception {
        var value = toValue("12:Hello World!");
        var query = PutQuery.builder().value(value).build();

        DHTValueTable valueTable = new DHTValueTable();
        valueTable.put(query);

        HashKey key = HashKey.fromHex("e5f96f6f38320f0f33959cb4d3d656452117aadb");
        assertThat(valueTable.find(key)).isPresent();
    }

    @Test
    @DisplayName("should create immutable data")
    void testMakeImmutable() throws Exception {
        var value = toValue("12:Hello World!");

        DHTValueTable valueTable = new DHTValueTable();

        var dhtValue = valueTable.makeImmutable(value);
        assertThat(dhtValue.getHashKey().getBytes())
                .asHexString()
                .isEqualToIgnoringCase("e5f96f6f38320f0f33959cb4d3d656452117aadb");
    }

    @Test
    @DisplayName("should put mutable data without salt")
    void testMutable() throws Exception {
        var value = toValue("12:Hello World!");
        byte[] publicKey = HexFormat.of().parseHex("77ff84905a91936367c01360803104f92432fcd904a43511876df5cdf3e7e548");
        byte[] signature = HexFormat.of()
                .parseHex(
                        "305ac8aeb6c9c151fa120f120ea2cfb923564e11552d06a5d856091e5e853cff1260d3f39e4999684aa92eb73ffd136e6f4f3ecbfda0ce53a1608ecd7ae21f01");

        var query = PutQuery.builder()
                .value(value)
                .seq(1L)
                .key(publicKey)
                .signature(signature)
                .build();

        DHTValueTable valueTable = new DHTValueTable();
        valueTable.put(query);

        HashKey key = HashKey.fromHex("4a533d47ec9c7d95b1ad75f576cffc641853b750");
        assertThat(valueTable.find(key)).isPresent();
    }

    @Test
    @DisplayName("should deny invalid signature with wrong seq")
    void testInvalidSignature() throws Exception {
        var value = toValue("12:Hello World!");
        byte[] publicKey = HexFormat.of().parseHex("77ff84905a91936367c01360803104f92432fcd904a43511876df5cdf3e7e548");
        byte[] signature = HexFormat.of()
                .parseHex(
                        "305ac8aeb6c9c151fa120f120ea2cfb923564e11552d06a5d856091e5e853cff1260d3f39e4999684aa92eb73ffd136e6f4f3ecbfda0ce53a1608ecd7ae21f01");

        var query = PutQuery.builder()
                .value(value)
                // signature is valid for seq=1
                .seq(3L)
                .key(publicKey)
                .signature(signature)
                .build();

        var valueTable = new DHTValueTable();
        var ex = catchThrowableOfType(DHTErrorException.class, () -> valueTable.put(query));

        assertThat(ex.getErrorMessage().getErrorCode()).isEqualTo(206);
    }

    @Test
    @DisplayName("should deny invalid signature with wrong value")
    void testInvalidSignature2() throws Exception {
        var value = toValue("11:Hello World");
        byte[] publicKey = HexFormat.of().parseHex("77ff84905a91936367c01360803104f92432fcd904a43511876df5cdf3e7e548");
        byte[] signature = HexFormat.of()
                .parseHex(
                        "305ac8aeb6c9c151fa120f120ea2cfb923564e11552d06a5d856091e5e853cff1260d3f39e4999684aa92eb73ffd136e6f4f3ecbfda0ce53a1608ecd7ae21f01");

        var query = PutQuery.builder()
                .value(value)
                .seq(1L)
                .key(publicKey)
                .signature(signature)
                .build();

        var valueTable = new DHTValueTable();
        var ex = catchThrowableOfType(DHTErrorException.class, () -> valueTable.put(query));

        assertThat(ex.getErrorMessage().getErrorCode()).isEqualTo(206);
    }

    @Test
    @DisplayName("should create mutable data without salt")
    void testMakeMutable() throws Exception {
        var value = toValue("12:Hello World!");
        byte[] publicKey = HexFormat.of().parseHex("e498be4af07bc4bec09d04459600b7108e56216f9d11f4652495418442f8b7f5");
        byte[] privateKey = HexFormat.of().parseHex("c3f8c41775d777e02b1a6227f3a0cc7316eecedab12eceae599d14394512e44b");
        byte[] signature = HexFormat.of()
                .parseHex(
                        "bf2e5d2e01f8cd4ec5114383951c300449ca652e5e77601fc94857a2997b93f1321b4b2f8b4b6383da59672493e1928d82aa71c53af29007ef9afee7d2c29d07");

        DHTValueTable valueTable = new DHTValueTable(new HashMap<>(), new ArrayList<>(), privateKey, publicKey);
        var dhtValue = valueTable.makeMutable(null, value);

        assertThat(dhtValue.getSalt()).isNull();
        assertThat(dhtValue.getKey()).containsExactly(publicKey);
        assertThat(dhtValue.getSignature()).containsExactly(signature);
        assertThat(dhtValue.getHashKey().getBytes()).isEqualTo(HashUtils.sha1(publicKey));
    }

    @Test
    @DisplayName("should put mutable data with salt")
    void testMutableSalt() throws Exception {
        var salt = "foobar".getBytes(StandardCharsets.UTF_8);
        var value = toValue("12:Hello World!");
        byte[] publicKey = HexFormat.of().parseHex("77ff84905a91936367c01360803104f92432fcd904a43511876df5cdf3e7e548");
        byte[] signature = HexFormat.of()
                .parseHex(
                        "6834284b6b24c3204eb2fea824d82f88883a3d95e8b4a21b8c0ded553d17d17ddf9a8a7104b1258f30bed3787e6cb896fca78c58f8e03b5f18f14951a87d9a08");

        var query = PutQuery.builder()
                .value(value)
                .seq(1L)
                .key(publicKey)
                .signature(signature)
                .salt(salt)
                .build();

        DHTValueTable valueTable = new DHTValueTable();
        valueTable.put(query);

        HashKey key = HashKey.fromHex("411eba73b6f087ca51a3795d9c8c938d365e32c1");
        assertThat(valueTable.find(key)).isPresent();
    }

    @Test
    @DisplayName("should create mutable data with salt")
    void testMakeMutableSalt() throws Exception {
        var value = toValue("12:Hello World!");
        byte[] salt = "foobar".getBytes(StandardCharsets.UTF_8);
        byte[] publicKey = HexFormat.of().parseHex("e498be4af07bc4bec09d04459600b7108e56216f9d11f4652495418442f8b7f5");
        byte[] privateKey = HexFormat.of().parseHex("c3f8c41775d777e02b1a6227f3a0cc7316eecedab12eceae599d14394512e44b");
        byte[] signature = HexFormat.of()
                .parseHex(
                        "55c7a0b8be5e9aa03e34dfa997a7fbc926886ddb39bbca2446ca7178783e1a9aab477b076ee9a96712cab73d35aed8e7af6f5aa3a719210a6d9685b1cb38d706");

        DHTValueTable valueTable = new DHTValueTable(new HashMap<>(), new ArrayList<>(), privateKey, publicKey);
        var dhtValue = valueTable.makeMutable(salt, value);

        assertThat(dhtValue.getKey()).containsExactly(publicKey);
        assertThat(dhtValue.getSignature()).containsExactly(signature);
        assertThat(dhtValue.getHashKey().getBytes()).isEqualTo(HashUtils.sha1(Arrays.concatenate(publicKey, salt)));
    }

    private static BEncodedValue toValue(String bencoded) {
        try {
            return BDecoder.bdecode(ByteBuffer.wrap(bencoded.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
