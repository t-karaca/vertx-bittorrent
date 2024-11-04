package vertx.bittorrent.model;

import com.beust.jcommander.Parameter;
import lombok.Getter;

@Getter
public class ClientOptions {
    @Parameter(names = "--server-port")
    private int serverPort;

    @Parameter
    private String torrentFilePath;
}
