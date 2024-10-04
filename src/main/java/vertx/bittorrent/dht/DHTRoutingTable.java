package vertx.bittorrent.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.vertx.core.Handler;
import io.vertx.core.net.SocketAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.Peer;
import vertx.bittorrent.dht.json.HashKeyDeserializer;
import vertx.bittorrent.dht.json.HashKeyKeyDeserializer;
import vertx.bittorrent.dht.json.HashKeySerializer;
import vertx.bittorrent.dht.json.SocketAddressDeserializer;
import vertx.bittorrent.dht.json.SocketAddressSerializer;

@Slf4j
@Getter
public class DHTRoutingTable {

    private static final ObjectMapper OBJECT_MAPPER;

    private final HashKey nodeId;
    private final List<DHTBucket> buckets;

    private final Map<HashKey, List<SocketAddress>> peerMap;

    @JsonIgnore
    private Handler<Void> updatedHandler;

    static {
        OBJECT_MAPPER = new ObjectMapper();

        OBJECT_MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);

        SimpleModule module = new SimpleModule();

        module.addSerializer(HashKey.class, new HashKeySerializer());
        module.addDeserializer(HashKey.class, new HashKeyDeserializer());

        module.addSerializer(SocketAddress.class, new SocketAddressSerializer());
        module.addDeserializer(SocketAddress.class, new SocketAddressDeserializer());

        module.addKeyDeserializer(HashKey.class, new HashKeyKeyDeserializer());

        OBJECT_MAPPER.registerModule(module);
    }

    public DHTRoutingTable() {
        nodeId = HashKey.random();
        buckets = new ArrayList<>();
        peerMap = new HashMap<>();

        buckets.add(DHTBucket.initial());

        buckets.forEach(b -> b.onRefresh(this::onBucketRefreshed));
    }

    @JsonCreator(mode = Mode.PROPERTIES)
    public DHTRoutingTable(
            @JsonProperty("nodeId") HashKey nodeId,
            @JsonProperty("buckets") Collection<DHTBucket> buckets,
            @JsonProperty("peerMap") Map<HashKey, List<SocketAddress>> peerMap) {
        this.nodeId = nodeId;
        this.buckets = new ArrayList<>(buckets);
        this.peerMap = peerMap;

        this.buckets.forEach(b -> b.onRefresh(this::onBucketRefreshed));
    }

    private void onBucketRefreshed(DHTBucket bucket) {
        if (updatedHandler != null) {
            updatedHandler.handle(null);
        }
    }

    public void onUpdated(Handler<Void> handler) {
        updatedHandler = handler;
    }

    public Optional<DHTNode> refreshNode(HashKey nodeId, SocketAddress address) {
        Optional<DHTNode> node = addNode(nodeId, address);

        node.ifPresent(DHTNode::refresh);

        return node;
    }

    public Optional<DHTNode> addNode(HashKey nodeId, SocketAddress address) {
        DHTBucket bucket = getBucketForId(nodeId);

        Optional<DHTNode> node = bucket.findNodeById(nodeId).or(() -> addNodeToBucket(bucket, nodeId, address));

        node.filter(n -> !n.isSameAddress(address)).ifPresent(n -> {
            log.error(
                    "Found conflicting id: {}, existing address: {}, new address: {}", nodeId, n.getAddress(), address);
            // throw DHTErrorException.create(203, "Conflicting node id");
        });

        return node;
    }

    private Optional<DHTNode> addNodeToBucket(DHTBucket bucket, HashKey nodeId, SocketAddress address) {
        DHTBucket target = bucket;

        if (target.isFull()) {
            if (target.canContain(this.nodeId)) {
                DHTBucket next = target.split();

                next.onRefresh(this::onBucketRefreshed);

                buckets.add(next);

                buckets.sort(Comparator.comparing(DHTBucket::getMin));

                if (next.canContain(nodeId)) {
                    target = next;
                }
            } else {
                target = null;
            }
        }

        if (target != null) {
            DHTNode node = new DHTNode(nodeId, address);

            target.purgeBadNodes();

            if (target.addNode(node)) {
                return Optional.of(node);
            }
        }

        return Optional.empty();
    }

    public DHTBucket getBucketForId(HashKey nodeId) {
        for (var bucket : buckets) {
            if (bucket.canContain(nodeId)) {
                return bucket;
            }
        }

        // should not happen
        return null;
    }

    public Optional<DHTNode> findNodeById(HashKey id) {
        return getBucketForId(id).findNodeById(id);
    }

    public List<DHTNode> findClosestNodesForId(HashKey nodeId) {
        int bucketIndex = -1;

        for (int i = 0; i < buckets.size(); i++) {
            if (buckets.get(i).canContain(nodeId)) {
                bucketIndex = i;
                break;
            }
        }

        DHTBucket bucket = buckets.get(bucketIndex);

        List<DHTNode> nodes = new ArrayList<>();

        nodes.addAll(bucket.getNodes());
        nodes.removeIf(DHTNode::isBad);

        int prevIndex = bucketIndex - 1;
        int nextIndex = bucketIndex + 1;
        while (nodes.size() < 8 && (prevIndex >= 0 || nextIndex < buckets.size())) {
            if (prevIndex >= 0) {
                nodes.addAll(buckets.get(prevIndex).getNodes());
            }

            if (nextIndex < buckets.size()) {
                nodes.addAll(buckets.get(nextIndex).getNodes());
            }

            nodes.removeIf(DHTNode::isBad);

            prevIndex--;
            nextIndex++;
        }

        return nodes.stream()
                .sorted(DHTNode.distanceComparator(nodeId))
                .limit(8)
                .toList();
    }

    public Optional<DHTBucket> findBucketToRefresh() {
        for (var bucket : buckets) {
            if (bucket.needsRefresh() && !bucket.isEmpty()) {
                return Optional.of(bucket);
            }
        }

        return Optional.empty();
    }

    public void addPeerForTorrent(byte[] infoHash, SocketAddress address) {
        HashKey key = new HashKey(infoHash);

        List<SocketAddress> peers = peerMap.computeIfAbsent(key, k -> new ArrayList<>());

        peers.add(address);
    }

    public List<byte[]> findPeersForTorrent(byte[] infoHash) {
        HashKey key = new HashKey(infoHash);

        List<SocketAddress> peers = peerMap.get(key);

        if (peers != null) {
            return peers.stream().map(Peer::toCompact).toList();
        }

        return Collections.emptyList();
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        OBJECT_MAPPER.writeValue(outputStream, this);
    }

    public static DHTRoutingTable parse(InputStream is) {
        try {
            return OBJECT_MAPPER.readValue(is, DHTRoutingTable.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
