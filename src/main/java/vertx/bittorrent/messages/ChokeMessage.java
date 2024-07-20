package vertx.bittorrent.messages;

import lombok.ToString;

@ToString
public class ChokeMessage extends Message {
    @Override
    public MessageType getMessageType() {
        return MessageType.CHOKE;
    }
}
