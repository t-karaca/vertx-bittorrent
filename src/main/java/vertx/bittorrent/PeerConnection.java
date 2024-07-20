package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.messages.BitfieldMessage;
import vertx.bittorrent.messages.ChokeMessage;
import vertx.bittorrent.messages.HandshakeMessage;
import vertx.bittorrent.messages.InterestedMessage;
import vertx.bittorrent.messages.Message;
import vertx.bittorrent.messages.RequestMessage;
import vertx.bittorrent.messages.UnchokeMessage;

@Slf4j
@RequiredArgsConstructor
public class PeerConnection {

    private final NetClient netClient;
    private final ClientState clientState;
    private final Peer peer;

    private final ProtocolHandler protocolHandler =
            new ProtocolHandler().setMessageHandler(this::handleProtocolMessage);

    private boolean connecting;
    private NetSocket socket;

    private Bitfield bitfield;
    private boolean choked = true;
    private boolean interested = false;

    public void reset() {
        protocolHandler.reset();

        choked = true;
        interested = false;
    }

    public Future<Void> connect() {
        if (connecting) {
            return Future.succeededFuture();
        }

        connecting = true;

        log.debug("Trying to connect to peer at {}", peer);

        return netClient
                .connect(peer.getPort(), peer.getAddress().getHostAddress())
                .onFailure(ex -> {
                    log.error("Could not connect to peer:", ex);
                    connecting = false;
                })
                .onSuccess(socket -> {
                    log.debug("Connected to peer at {}", peer);

                    this.socket = socket;

                    socket.handler(protocolHandler::readBuffer);

                    sendMessage(new HandshakeMessage(clientState.getTorrent().getInfoHash(), clientState.getPeerId()));

                    socket.closeHandler(v -> {
                        this.socket = null;
                        connecting = false;
                    });
                })
                .mapEmpty();
    }

    public Future<Void> close() {
        if (socket == null) {
            return Future.succeededFuture();
        }

        return socket.close();
    }

    private void handleProtocolMessage(Message message) {
        log.trace("Received {} from {}", message.getMessageType().name(), peer);

        if (message instanceof BitfieldMessage bitfieldMessage) {
            this.bitfield = bitfieldMessage.getBitfield();

            sendMessage(new InterestedMessage());
            sendMessage(new UnchokeMessage());
        } else if (message instanceof ChokeMessage) {
            choked = true;
        } else if (message instanceof UnchokeMessage) {
            choked = false;

            sendMessage(new RequestMessage(100, 0, ProtocolHandler.MAX_BLOCK_SIZE));
        }
    }

    private Future<Void> sendMessage(Message message) {
        if (socket == null) {
            return Future.succeededFuture();
        }

        log.trace("Sending {} to {}", message.getMessageType().name(), peer);
        return socket.write(message.toBuffer());
    }

    public static PeerConnection create(Vertx vertx, ClientState clientState, Peer peer) {
        NetClient client = vertx.createNetClient();

        return new PeerConnection(client, clientState, peer);
    }
}
