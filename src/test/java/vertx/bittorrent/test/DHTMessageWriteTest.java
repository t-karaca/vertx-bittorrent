package vertx.bittorrent.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import vertx.bittorrent.dht.HashKey;
import vertx.bittorrent.dht.messages.AnnouncePeerQuery;
import vertx.bittorrent.dht.messages.AnnouncePeerResponse;
import vertx.bittorrent.dht.messages.DHTErrorMessage;
import vertx.bittorrent.dht.messages.DHTMessage;
import vertx.bittorrent.dht.messages.DHTMessageType;
import vertx.bittorrent.dht.messages.FindNodeQuery;
import vertx.bittorrent.dht.messages.FindNodeResponse;
import vertx.bittorrent.dht.messages.GetPeersQuery;
import vertx.bittorrent.dht.messages.GetPeersResponse;
import vertx.bittorrent.dht.messages.PingQuery;
import vertx.bittorrent.dht.messages.PingResponse;

public class DHTMessageWriteTest {
    @Test
    void writeError() {
        var payload = "d1:eli201e23:A Generic Error Ocurrede1:t2:aa1:y1:ee";

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.ERROR)
                .setPayload(DHTErrorMessage.builder()
                        .errorCode(201)
                        .errorMessage("A Generic Error Ocurred")
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writePingQuery() {
        var payload = "d1:ad2:id20:abcdefghij0123456789e1:q4:ping1:t2:aa1:y1:qe";
        var id = "abcdefghij0123456789";

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.QUERY)
                .setPayload(PingQuery.builder().nodeId(HashKey.fromString(id)).build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writePingResponse() {
        var payload = "d1:rd2:id20:mnopqrstuvwxyz123456e1:t2:aa1:y1:re";
        var id = "mnopqrstuvwxyz123456";

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.RESPONSE)
                .setPayload(
                        PingResponse.builder().nodeId(HashKey.fromString(id)).build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writeFindNodeQuery() {
        var payload = "d1:ad2:id20:abcdefghij01234567896:target20:mnopqrstuvwxyz123456e1:q9:find_node1:t2:aa1:y1:qe";
        var id = "abcdefghij0123456789";
        var target = "mnopqrstuvwxyz123456";

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.QUERY)
                .setPayload(FindNodeQuery.builder()
                        .nodeId(HashKey.fromString(id))
                        .target(HashKey.fromString(target))
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writeFindNodeResponse() {
        var payload = "d1:rd2:id20:0123456789abcdefghij5:nodes9:def456...e1:t2:aa1:y1:re";
        var id = "0123456789abcdefghij";
        var target = "def456...".getBytes(StandardCharsets.UTF_8);

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.RESPONSE)
                .setPayload(FindNodeResponse.builder()
                        .nodeId(HashKey.fromString(id))
                        .nodes(target)
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writeGetPeersQuery() {
        var payload = "d1:ad2:id20:abcdefghij01234567899:info_hash20:mnopqrstuvwxyz123456e1:q9:get_peers1:t2:aa1:y1:qe";
        var id = "abcdefghij0123456789";
        var infoHash = "mnopqrstuvwxyz123456";

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.QUERY)
                .setPayload(GetPeersQuery.builder()
                        .nodeId(HashKey.fromString(id))
                        .infoHash(infoHash.getBytes(StandardCharsets.UTF_8))
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writeGetPeersResponseWithPeers() {
        var payload = "d1:rd2:id20:abcdefghij01234567895:token8:aoeusnth6:valuesl6:axje.u6:idhtnmee1:t2:aa1:y1:re";
        var id = "abcdefghij0123456789";
        var token = "aoeusnth".getBytes(StandardCharsets.UTF_8);
        var peers = List.of("axje.u".getBytes(StandardCharsets.UTF_8), "idhtnm".getBytes(StandardCharsets.UTF_8));

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.RESPONSE)
                .setPayload(GetPeersResponse.builder()
                        .nodeId(HashKey.fromString(id))
                        .token(token)
                        .values(peers)
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writeGetPeersResponseWithNodes() {
        var payload = "d1:rd2:id20:abcdefghij01234567895:nodes9:def456...5:token8:aoeusnthe1:t2:aa1:y1:re";
        var id = "abcdefghij0123456789";
        var token = "aoeusnth".getBytes(StandardCharsets.UTF_8);
        var nodes = "def456...".getBytes(StandardCharsets.UTF_8);

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.RESPONSE)
                .setPayload(GetPeersResponse.builder()
                        .nodeId(HashKey.fromString(id))
                        .token(token)
                        .nodes(nodes)
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writeAnnouncePeerQuery() {
        var payload =
                "d1:ad2:id20:abcdefghij012345678912:implied_porti1e9:info_hash20:mnopqrstuvwxyz1234564:porti6881e5:token8:aoeusnthe1:q13:announce_peer1:t2:aa1:y1:qe";
        var id = "abcdefghij0123456789";
        var infoHash = "mnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8);
        var token = "aoeusnth".getBytes(StandardCharsets.UTF_8);

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.QUERY)
                .setPayload(AnnouncePeerQuery.builder()
                        .nodeId(HashKey.fromString(id))
                        .infoHash(infoHash)
                        .impliedPort(true)
                        .token(token)
                        .port(6881)
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }

    @Test
    void writeAnnouncePeerResponse() {
        var payload = "d1:rd2:id20:mnopqrstuvwxyz123456e1:t2:aa1:y1:re";
        var id = "mnopqrstuvwxyz123456";

        var message = new DHTMessage()
                .setTransactionId("aa")
                .setType(DHTMessageType.RESPONSE)
                .setPayload(AnnouncePeerResponse.builder()
                        .nodeId(HashKey.fromString(id))
                        .build());

        assertThat(message.toBuffer().getBytes()).asString().isEqualTo(payload);
    }
}
