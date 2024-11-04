package vertx.bittorrent.utils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UriBuilder {

    private String protocol;
    private String host;
    private int port;
    private final StringBuilder pathBuilder = new StringBuilder();
    private final StringBuilder paramsBuilder = new StringBuilder();

    private boolean hasQueryParams = false;

    public URI build() {
        return URI.create(paramsBuilder.toString());
    }

    public UriBuilder queryParam(String param, byte[] value) {
        appendQueryParamKey(param);

        for (byte b : value) {
            if (b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z' || b >= '0' && b <= '9') {
                paramsBuilder.append((char) b);
            } else {
                paramsBuilder.append("%").append(String.format("%02x", b));
            }
        }

        return this;
    }

    public UriBuilder queryParam(String param, String value) {
        appendQueryParamKey(param);

        paramsBuilder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));

        return this;
    }

    public UriBuilder rawQueryParam(String param, String value) {
        appendQueryParamKey(param);

        paramsBuilder.append(value);

        return this;
    }

    public UriBuilder queryParam(String param, long value) {
        appendQueryParamKey(param);

        paramsBuilder.append(value);

        return this;
    }

    private void appendQueryParamKey(String param) {
        if (!hasQueryParams) {
            paramsBuilder.append('?');
        } else {
            paramsBuilder.append('&');
        }

        paramsBuilder.append(param);
        paramsBuilder.append('=');

        hasQueryParams = true;
    }

    public static UriBuilder fromUriString(String uriString) {
        UriBuilder builder = new UriBuilder();

        builder.paramsBuilder.append(uriString);
        builder.hasQueryParams = uriString.contains("?");

        return builder;
    }
}
