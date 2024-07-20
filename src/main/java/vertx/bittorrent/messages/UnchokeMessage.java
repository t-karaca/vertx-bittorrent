package vertx.bittorrent.messages;

import lombok.ToString;

@ToString
public class UnchokeMessage extends Message {
    @Override
    public MessageType getMessageType() {
        return MessageType.UNCHOKE;
    }
}
