package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.PieceState.BlockState;
import vertx.bittorrent.messages.BitfieldMessage;
import vertx.bittorrent.messages.ChokeMessage;
import vertx.bittorrent.messages.HandshakeMessage;
import vertx.bittorrent.messages.InterestedMessage;
import vertx.bittorrent.messages.Message;
import vertx.bittorrent.messages.NotInterestedMessage;
import vertx.bittorrent.messages.PieceMessage;
import vertx.bittorrent.messages.RequestMessage;
import vertx.bittorrent.messages.UnchokeMessage;

@Slf4j
public class PeerConnection {

    private final NetSocket socket;
    private final ClientState clientState;

    @Getter
    private final Peer peer;

    private final ProtocolHandler protocolHandler =
            new ProtocolHandler().setMessageHandler(this::handleProtocolMessage);

    @Getter
    private Bitfield bitfield;

    @Getter
    private boolean choked = true;

    @Getter
    private boolean interested = false;

    @Getter
    private boolean remoteChoked = true;

    @Getter
    private boolean remoteInterested = false;

    @Getter
    private int bytesDownloaded = 0;

    @Getter
    @Setter
    private int previousBytesDownloaded = 0;

    @Getter
    @Setter
    private float downloadRate = 0.0f;

    private int currentRequestCount = 0;
    private int requestLimit = 6;

    private Map<Integer, PieceState> pieceStates = new HashMap<>();

    private Handler<Bitfield> bitfieldHandler;
    private Handler<Void> chokedHandler;
    private Handler<Void> unchokedHandler;
    private Handler<Void> interestedHandler;
    private Handler<Void> notInterestedHandler;
    private Handler<RequestMessage> requestHandler;
    private Handler<PieceMessage> blockHandler;
    private Handler<Integer> pieceHandler;

    private PeerConnection(NetSocket socket, ClientState clientState, Peer peer) {
        log.debug("Connected to peer at {}", peer);

        this.socket = socket;
        this.clientState = clientState;
        this.peer = peer;

        socket.handler(protocolHandler::readBuffer);

        sendMessage(new HandshakeMessage(clientState.getTorrent().getInfoHash(), clientState.getPeerId()));
    }

    public boolean isPieceRequested(int index) {
        return pieceStates.containsKey(index);
    }

    public PeerConnection onBitfield(Handler<Bitfield> handler) {
        bitfieldHandler = handler;
        return this;
    }

    public PeerConnection onChoked(Handler<Void> handler) {
        chokedHandler = handler;
        return this;
    }

    public PeerConnection onUnchoked(Handler<Void> handler) {
        unchokedHandler = handler;
        return this;
    }

    public PeerConnection onInterested(Handler<Void> handler) {
        interestedHandler = handler;
        return this;
    }

    public PeerConnection onNotInterested(Handler<Void> handler) {
        notInterestedHandler = handler;
        return this;
    }

    public PeerConnection onRequest(Handler<RequestMessage> handler) {
        requestHandler = handler;
        return this;
    }

    public PeerConnection onBlockReceived(Handler<PieceMessage> handler) {
        blockHandler = handler;
        return this;
    }

    public PeerConnection onPieceCompleted(Handler<Integer> handler) {
        pieceHandler = handler;
        return this;
    }

    public PeerConnection onClosed(Handler<Void> handler) {
        socket.closeHandler(handler);
        return this;
    }

    public Future<Void> close() {
        return socket.close();
    }

    public void choke() {
        choked = true;
        sendMessage(new ChokeMessage());
    }

    public void unchoke() {
        choked = false;
        sendMessage(new UnchokeMessage());
    }

    public void interested() {
        interested = true;
        sendMessage(new InterestedMessage());
    }

    public void notInterested() {
        interested = false;
        sendMessage(new NotInterestedMessage());
    }

    public void requestPiece(int pieceIndex) {
        if (!pieceStates.containsKey(pieceIndex)) {
            long pieceLength = clientState.getTorrent().getLengthForPiece(pieceIndex);

            pieceStates.put(pieceIndex, new PieceState(pieceLength));

            processRequests();
        }
    }

    private boolean canRequest() {
        if (remoteChoked || pieceStates.isEmpty()) {
            return false;
        }

        for (var pieceState : pieceStates.values()) {
            for (int i = 0; i < pieceState.getBlocksCount(); i++) {
                if (pieceState.getBlockState(i) == BlockState.Queued) {
                    return true;
                }
            }
        }

        return false;
    }

    private void processRequests() {
        while (currentRequestCount < requestLimit && canRequest()) {
            for (var entry : pieceStates.entrySet()) {
                int pieceIndex = entry.getKey();
                var pieceState = entry.getValue();

                for (int i = 0; i < pieceState.getBlocksCount(); i++) {
                    if (pieceState.getBlockState(i) == BlockState.Queued) {
                        currentRequestCount++;
                        pieceState.setBlockState(i, BlockState.Requested);
                        sendMessage(new RequestMessage(
                                pieceIndex, pieceState.getBlockOffset(i), pieceState.getBlockSize(i)));
                        break;
                    }
                }
            }
        }
    }

    private void handleProtocolMessage(Message message) {
        log.debug("[{}] Received {}", peer, message.getMessageType().name());

        if (message instanceof BitfieldMessage bitfieldMessage) {
            bitfield = bitfieldMessage.getBitfield();

            if (bitfieldHandler != null) {
                bitfieldHandler.handle(bitfield);
            }
        } else if (message instanceof ChokeMessage) {
            remoteChoked = true;

            if (chokedHandler != null) {
                chokedHandler.handle(null);
            }
        } else if (message instanceof UnchokeMessage) {
            remoteChoked = false;

            if (unchokedHandler != null) {
                unchokedHandler.handle(null);
            }
        } else if (message instanceof InterestedMessage) {
            remoteInterested = true;

            if (interestedHandler != null) {
                interestedHandler.handle(null);
            }
        } else if (message instanceof NotInterestedMessage) {
            remoteInterested = false;

            if (notInterestedHandler != null) {
                notInterestedHandler.handle(null);
            }
        } else if (message instanceof RequestMessage requestMessage) {
            if (requestHandler != null) {
                requestHandler.handle(requestMessage);
            }
        } else if (message instanceof PieceMessage pieceMessage) {
            bytesDownloaded += pieceMessage.getData().length();

            var pieceState = pieceStates.get(pieceMessage.getPieceIndex());
            if (pieceState != null) {
                if (pieceState.getBlockStateByOffset(pieceMessage.getBegin()) == BlockState.Requested) {
                    // piece was expected
                    pieceState.setBlockStateByOffset(pieceMessage.getBegin(), BlockState.Downloaded);
                    currentRequestCount--;

                    if (blockHandler != null) {
                        blockHandler.handle(pieceMessage);
                    }

                    if (pieceState.isCompleted()) {
                        pieceStates.remove(pieceMessage.getPieceIndex());

                        if (pieceHandler != null) {
                            pieceHandler.handle(pieceMessage.getPieceIndex());
                        }
                    }
                }
            }

            processRequests();
        }
    }

    private Future<Void> sendMessage(Message message) {
        if (socket == null) {
            return Future.succeededFuture();
        }

        log.debug("[{}] Sending {}", peer, message.getMessageType().name());
        return socket.write(message.toBuffer());
    }

    public static Future<PeerConnection> connect(NetClient client, ClientState clientState, Peer peer) {
        log.debug("Trying to connect to peer at {}", peer);

        return client.connect(peer.getPort(), peer.getAddress().getHostAddress())
                .onFailure(ex -> log.error("Could not connect to peer:", ex))
                .map(socket -> new PeerConnection(socket, clientState, peer));
    }
}
