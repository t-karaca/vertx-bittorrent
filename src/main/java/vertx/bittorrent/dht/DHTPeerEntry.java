package vertx.bittorrent.dht;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DHTPeerEntry {
    public static final Duration STALE_ENTRY_DURATION = Duration.ofHours(1);

    private Instant announcedAt;
    private SocketAddress peerAddress;

    public DHTPeerEntry(SocketAddress address) {
        this.announcedAt = Instant.now();
        this.peerAddress = address;
    }

    public void refresh() {
        this.announcedAt = Instant.now();
    }

    @JsonIgnore
    public boolean isStale() {
        return Duration.between(announcedAt, Instant.now()).compareTo(STALE_ENTRY_DURATION) >= 0;
    }
}
