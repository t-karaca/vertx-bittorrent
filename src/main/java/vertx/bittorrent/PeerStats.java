package vertx.bittorrent;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeerStats {

    public static class TorrentStats {

        private long connectedAt = -1;
        private long connectionDuration = 0;

        @Getter
        private int uploadedBytes;

        @Getter
        private int downloadedBytes;

        public void addDownloadedBytes(int bytes) {
            downloadedBytes += bytes;
        }

        public void addUploadedBytes(int bytes) {
            uploadedBytes += bytes;
        }

        public void setConnected(boolean connected) {
            if (!connected && connectedAt != -1) {
                connectionDuration += (System.currentTimeMillis() - connectedAt);
                connectedAt = -1;
            }

            if (connected && connectedAt == -1) {
                connectedAt = System.currentTimeMillis();
            }
        }

        public double getConnectionDuration() {
            long duration = connectionDuration;

            if (connectedAt != -1) {
                duration += (System.currentTimeMillis() - connectedAt);
            }

            return duration / 1000.0;
        }

        public boolean isConnected() {
            return connectedAt != -1;
        }
    }

    // private long connectedAt = -1;
    // private long connectionDuration = 0;
    // private int connectionCount = 0;
    //
    // private int uploadedBytes;
    // private int downloadedBytes;

    private final String address;

    private boolean freeRider;

    private Map<String, TorrentStats> torrents = new HashMap<>();

    public PeerStats(String address) {
        this.address = address;
    }

    public TorrentStats statsForTorrent(Torrent torrent) {
        String infoHash = torrent.getHexEncodedInfoHash();

        return torrents.computeIfAbsent(infoHash, key -> new TorrentStats());
    }

    public int getTotalUploadedBytes() {
        return torrents.values().stream()
                .reduce(0, (total, stats) -> total + stats.getUploadedBytes(), (a, b) -> a + b);
    }

    public int getTotalDownloadedBytes() {
        return torrents.values().stream()
                .reduce(0, (total, stats) -> total + stats.getDownloadedBytes(), (a, b) -> a + b);
    }

    // public void addDownloadedBytes(int bytes) {
    //     downloadedBytes += bytes;
    // }
    //
    // public void addUploadedBytes(int bytes) {
    //     uploadedBytes += bytes;
    // }

    // public void addConnection() {
    //     if (connectionCount == 0) {
    //         connectedAt = System.currentTimeMillis();
    //     }
    //
    //     connectionCount++;
    // }
    //
    // public void removeConnection() {
    //     connectionCount = Math.max(0, connectionCount - 1);
    //
    //     if (connectionCount == 0 && connectedAt != -1) {
    //         connectionDuration += System.currentTimeMillis() - connectedAt;
    //         connectedAt = -1;
    //     }
    // }
    //
    // public double getConnectionDuration() {
    //     long duration = connectionDuration;
    //
    //     if (connectedAt != -1) {
    //         duration += (System.currentTimeMillis() - connectedAt);
    //     }
    //
    //     return duration / 1000.0;
    // }
}
