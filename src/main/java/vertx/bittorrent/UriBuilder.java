package vertx.bittorrent;

import java.net.URI;

public class UriBuilder {

    private final StringBuilder builder = new StringBuilder();

    private boolean hasQueryParams = false;

    public URI build() {
        return URI.create(builder.toString());
    }

    public UriBuilder queryParam(String param, byte[] value) {
        if (!hasQueryParams) {
            builder.append('?');
        } else {
            builder.append('&');
        }

        builder.append(param);
        builder.append('=');

        for (byte b : value) {
            if (b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z' || b >= '0' && b <= '9') {
                builder.append((char) b);
            } else {
                builder.append("%").append(String.format("%02x", b));
            }
        }

        hasQueryParams = true;

        return this;
    }

    public UriBuilder rawQueryParam(String param, String value) {
        if (!hasQueryParams) {
            builder.append('?');
        } else {
            builder.append('&');
        }

        builder.append(param);
        builder.append('=');
        builder.append(value);

        hasQueryParams = true;

        return this;
    }

    public static UriBuilder fromUriString(String uriString) {
        UriBuilder builder = new UriBuilder();

        builder.builder.append(uriString);
        builder.hasQueryParams = uriString.contains("?");

        return builder;
    }
}
