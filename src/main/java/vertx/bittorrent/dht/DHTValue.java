package vertx.bittorrent.dht;

import be.adaxisoft.bencode.BEncodedValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import vertx.bittorrent.model.HashKey;

@Getter
@Setter
public class DHTValue {
    private HashKey hashKey;
    private BEncodedValue value;
    private byte[] key;
    private byte[] salt;
    private byte[] signature;
    private long sequenceNumber;

    @JsonIgnore
    public boolean isMutable() {
        return key != null;
    }
}
