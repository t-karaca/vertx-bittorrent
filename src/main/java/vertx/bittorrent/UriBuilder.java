package vertx.bittorrent;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UriBuilder {

    private final StringBuilder builder = new StringBuilder();

    private boolean hasQueryParams = false;

    public URI build() {
        return URI.create(builder.toString());
    }

    public UriBuilder queryParam(String param, byte[] value) {
        appendQueryParamKey(param);

        for (byte b : value) {
            if (b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z' || b >= '0' && b <= '9') {
                builder.append((char) b);
            } else {
                builder.append("%").append(String.format("%02x", b));
            }
        }

        return this;
    }

    public UriBuilder queryParam(String param, String value) {
        appendQueryParamKey(param);

        builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));

        return this;
    }

    public UriBuilder rawQueryParam(String param, String value) {
        appendQueryParamKey(param);

        builder.append(value);

        return this;
    }

    public UriBuilder queryParam(String param, long value) {
        appendQueryParamKey(param);

        builder.append(value);

        return this;
    }

    private void appendQueryParamKey(String param) {
        if (!hasQueryParams) {
            builder.append('?');
        } else {
            builder.append('&');
        }

        builder.append(param);
        builder.append('=');

        hasQueryParams = true;
    }

    public static UriBuilder fromUriString(String uriString) {
        UriBuilder builder = new UriBuilder();

        builder.builder.append(uriString);
        builder.hasQueryParams = uriString.contains("?");

        return builder;
    }
}
