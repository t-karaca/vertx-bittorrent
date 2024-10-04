// package vertx.bittorrent.test;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.vertx.core.net.SocketAddress;
// import java.io.FileInputStream;
// import java.io.FileOutputStream;
// import org.junit.jupiter.api.Test;
// import vertx.bittorrent.dht.DHTNodeId;
// import vertx.bittorrent.dht.DHTRoutingTable;
//
// public class JsonTest {
//
//     @Test
//     void bitTest() {
//         assertThat(0b10000000 >>> 1).isEqualTo(0b01000000);
//         assertThat(0b01000000 >>> 1).isEqualTo(0b00100000);
//     }
//
//     @Test
//     void test() throws Exception {
//         DHTRoutingTable table = new DHTRoutingTable();
//
//         var nodeId1 = DHTNodeId.random();
//         var nodeId2 = DHTNodeId.random();
//
//         table.refreshNode(nodeId1, SocketAddress.inetSocketAddress(1234, "1.2.3.4"));
//         table.refreshNode(nodeId2, SocketAddress.inetSocketAddress(5342, "34.123.54.21"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "24.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "25.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "26.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "27.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "28.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "29.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "210.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "211.12.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "212.12.3.123"));
//
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.13.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.14.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.15.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.16.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.17.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.18.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.19.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.20.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.21.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.22.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.23.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.24.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.25.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.26.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.27.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.28.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.29.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.30.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.31.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.32.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.33.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.34.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.35.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.36.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.37.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.38.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.39.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.40.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.41.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.42.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.43.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.44.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.45.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.46.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.47.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.48.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.49.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.50.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.51.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.52.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.53.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.54.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.55.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.56.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.57.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.58.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.59.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.60.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.61.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.62.3.123"));
//         table.refreshNode(DHTNodeId.random(), SocketAddress.inetSocketAddress(5342, "23.63.3.123"));
//
//         table.addPeerForTorrent(DHTNodeId.random().getBytes(), SocketAddress.inetSocketAddress(5342, "23.63.3.123"));
//
//         try (var outputStream = new FileOutputStream("test-dht.json")) {
//             table.writeTo(outputStream);
//         }
//
//         try (var inputStream = new FileInputStream("test-dht.json")) {
//             DHTRoutingTable parsed = DHTRoutingTable.parse(inputStream);
//
//             assertThat(parsed.findNodeById(nodeId1)).isNotEmpty().hasValueSatisfying(v -> {
//                 assertThat(v.getNodeId().getBytes()).asHexString().isEqualToIgnoringCase(nodeId1.toString());
//                 assertThat(v.getAddress().hostAddress()).isEqualTo("1.2.3.4");
//                 assertThat(v.getAddress().port()).isEqualTo(1234);
//             });
//
//             assertThat(parsed.findNodeById(nodeId2)).isNotEmpty().hasValueSatisfying(v -> {
//                 assertThat(v.getNodeId().getBytes()).asHexString().isEqualToIgnoringCase(nodeId2.toString());
//                 assertThat(v.getAddress().hostAddress()).isEqualTo("34.123.54.21");
//                 assertThat(v.getAddress().port()).isEqualTo(5342);
//             });
//         }
//     }
// }
