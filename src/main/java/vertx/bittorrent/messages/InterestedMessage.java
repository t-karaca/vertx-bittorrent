package vertx.bittorrent.messages;

import lombok.ToString;

@ToString
public class InterestedMessage extends Message {
    @Override
    public MessageType getMessageType() {
        return MessageType.INTERESTED;
    }
}
