package vertx.bittorrent;

import io.vertx.core.buffer.Buffer;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Piece {
    private final int index;
    private final Buffer data;
    private final byte[] hash;
    private final boolean hashValid;
}
