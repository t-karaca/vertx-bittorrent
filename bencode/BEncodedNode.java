package vertx.bittorrent.bencode;

public interface BEncodedNode {
    default boolean isDict() {
        return false;
    }

    default boolean isList() {
        return false;
    }

    default boolean isString() {
        return false;
    }

    default boolean isNumber() {
        return false;
    }

    default BEncodedDict asDict() {
        return (BEncodedDict) this;
    }

    default BEncodedList asList() {
        return (BEncodedList) this;
    }

    default String asStr() {
        throw new RuntimeException("node is not a string");
    }

    default int asInt() {
        throw new RuntimeException("node is not a number");
    }

    default long asLong() {
        throw new RuntimeException("node is not a number");
    }

    default BEncodedString asString() {
        return (BEncodedString) this;
    }

    default BEncodedNumber asNumber() {
        return (BEncodedNumber) this;
    }

    // void serialize(OutputStream os) throws IOException;
}
