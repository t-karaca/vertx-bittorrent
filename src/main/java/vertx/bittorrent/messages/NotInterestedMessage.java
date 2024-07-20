package vertx.bittorrent.messages;

import lombok.ToString;

@ToString
public class NotInterestedMessage extends Message {
    @Override
    public MessageType getMessageType() {
        return MessageType.NOT_INTERESTED;
    }
}
