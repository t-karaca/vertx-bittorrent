package vertx.bittorrent.model;

import com.beust.jcommander.Parameter;
import java.util.List;
import lombok.Getter;

@Getter
public class ClientOptions {
    @Parameter(names = "--server-port")
    private int serverPort = 6881;

    @Parameter(names = "--debug")
    private boolean debug = false;

    @Parameter(names = "--dht-disable")
    private boolean dhtDisable = false;

    @Parameter(names = "--dht-bootstrap-node")
    private String dhtBootstrapNode;

    @Parameter(names = "--dht-port")
    private int dhtPort = 6881;

    @Parameter(names = "--dht-debug")
    private boolean dhtDebug = false;

    @Parameter
    private List<String> torrentFilePaths;
}
