package vertx.bittorrent.dht.exception;

import lombok.Getter;
import vertx.bittorrent.dht.messages.DHTErrorMessage;

@Getter
public class DHTErrorException extends RuntimeException {
    private final DHTErrorMessage errorMessage;

    public DHTErrorException(DHTErrorMessage errorMessage) {
        super(errorMessage.getErrorCode() + " " + errorMessage.getErrorMessage());

        this.errorMessage = errorMessage;
    }

    public static DHTErrorException create(int errorCode, String errorMessage) {
        return new DHTErrorException(DHTErrorMessage.builder()
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build());
    }
}
