package vertx.bittorrent;

import io.vertx.core.net.SocketAddress;

public final class AddressUtils {
    private AddressUtils() {}

    public static SocketAddress addressFromString(String value) {
        String[] parts = value.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("address must be given in form '<host>:<port>'");
        }

        try {
            int port = Integer.parseInt(parts[1]);

            SocketAddress socketAddress = SocketAddress.inetSocketAddress(port, parts[0]);

            return socketAddress;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse address from '" + value + "': " + e.getMessage(), e);
        }
    }
}
