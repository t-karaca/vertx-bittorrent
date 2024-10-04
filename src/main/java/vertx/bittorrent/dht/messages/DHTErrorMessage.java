package vertx.bittorrent.dht.messages;

import be.adaxisoft.bencode.BEncodedValue;
import be.adaxisoft.bencode.InvalidBEncodingException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class DHTErrorMessage implements Payload {

    private final int errorCode;
    private final String errorMessage;

    @Override
    public BEncodedValue value() {
        try {
            List<BEncodedValue> list = new ArrayList<>(2);

            list.add(new BEncodedValue(errorCode));
            list.add(new BEncodedValue(errorMessage));

            return new BEncodedValue(list);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static DHTErrorMessage from(BEncodedValue value) {
        try {
            List<BEncodedValue> list = value.getList();
            int errorCode = list.get(0).getInt();
            String errorMessage = list.get(1).getString();

            return builder().errorCode(errorCode).errorMessage(errorMessage).build();
        } catch (InvalidBEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
