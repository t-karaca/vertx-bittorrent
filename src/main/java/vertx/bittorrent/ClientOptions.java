package vertx.bittorrent;

import com.beust.jcommander.Parameter;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ClientOptions {
    @Parameter(names = "--id")
    private int id;

    @Parameter(names = "--images-path")
    private String imagesPath;

    @Parameter(names = "--labels-path")
    private String labelsPath;

    @Parameter(names = "--data-dir")
    private String dataDir;

    @Parameter(names = "--torrent-dir")
    private String torrentDir;

    @Parameter(names = "--server-port")
    private int serverPort;

    @Parameter
    private String torrentFilePath;
}
