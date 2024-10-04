package vertx.bittorrent.dht.messages;

public enum DHTMessageType {
    QUERY("q", "a"),
    RESPONSE("r", "r"),
    ERROR("e", "e");

    public final String value;
    public final String payloadKey;

    DHTMessageType(String value, String payloadKey) {
        this.value = value;
        this.payloadKey = payloadKey;
    }

    public static DHTMessageType fromValue(String value) {
        for (var type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Could not resolve type for '" + value + "'");
    }
}
