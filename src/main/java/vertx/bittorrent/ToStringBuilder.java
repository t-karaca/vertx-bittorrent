package vertx.bittorrent;

import java.util.Collection;
import java.util.HexFormat;

public class ToStringBuilder {
    private final StringBuilder stringBuilder;

    private boolean addFieldComma = false;

    private ToStringBuilder(Class<?> clazz) {
        stringBuilder = new StringBuilder(clazz.getSimpleName());
        stringBuilder.append('(');
    }

    public ToStringBuilder field(String field, Object object) {
        if (object != null) {
            field(field, object.toString());
        }

        return this;
    }

    public ToStringBuilder field(String field, String str) {
        if (str != null) {
            if (addFieldComma) {
                stringBuilder.append(", ");
            }

            stringBuilder.append(field);
            stringBuilder.append('=');
            stringBuilder.append(str);

            addFieldComma = true;
        }

        return this;
    }

    public ToStringBuilder field(String field, boolean value) {
        if (addFieldComma) {
            stringBuilder.append(", ");
        }

        stringBuilder.append(field);
        stringBuilder.append('=');
        stringBuilder.append(value);

        addFieldComma = true;

        return this;
    }

    public ToStringBuilder field(String field, int value) {
        if (addFieldComma) {
            stringBuilder.append(", ");
        }

        stringBuilder.append(field);
        stringBuilder.append('=');
        stringBuilder.append(value);

        addFieldComma = true;

        return this;
    }

    public ToStringBuilder field(String field, long value) {
        if (addFieldComma) {
            stringBuilder.append(", ");
        }

        stringBuilder.append(field);
        stringBuilder.append('=');
        stringBuilder.append(value);

        addFieldComma = true;

        return this;
    }

    public ToStringBuilder field(String field, byte[] value) {
        if (value != null) {
            if (addFieldComma) {
                stringBuilder.append(", ");
            }

            stringBuilder.append(field);
            stringBuilder.append('=');
            stringBuilder.append(HexFormat.of().formatHex(value));

            addFieldComma = true;
        }

        return this;
    }

    public <T> ToStringBuilder field(String field, Collection<T> value) {
        if (value != null) {
            if (addFieldComma) {
                stringBuilder.append(", ");
            }

            stringBuilder.append(field);
            stringBuilder.append('=');
            stringBuilder.append('[');

            boolean addComma = false;

            for (var element : value) {
                if (addComma) {
                    stringBuilder.append(", ");
                }

                stringBuilder.append(element);

                addComma = true;
            }

            stringBuilder.append(']');

            addFieldComma = true;
        }

        return this;
    }

    public String build() {
        stringBuilder.append(')');

        return stringBuilder.toString();
    }

    public static ToStringBuilder builder(Class<?> clazz) {
        return new ToStringBuilder(clazz);
    }
}
