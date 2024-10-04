package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;

public interface Payload {
    BEncodedValue value();
}
