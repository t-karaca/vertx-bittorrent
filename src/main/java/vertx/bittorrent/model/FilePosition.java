package vertx.bittorrent.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FilePosition {
    private final int fileIndex;
    private final long offset;

    private final FileInfo fileInfo;
}
