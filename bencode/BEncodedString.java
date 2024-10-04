package vertx.bittorrent.bencode;

public class BEncodedString implements BEncodedNode {
    @Override
    public boolean isString() {
        return true;
    }
}
