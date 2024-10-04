package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;

public interface QueryPayload<T extends Payload> extends Payload {
    String queryType();

    T parseResponse(BEncodedValue value);
}
