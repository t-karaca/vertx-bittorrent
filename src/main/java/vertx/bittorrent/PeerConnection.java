package vertx.bittorrent;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import vertx.bittorrent.PieceState.BlockState;
import vertx.bittorrent.messages.BitfieldMessage;
import vertx.bittorrent.messages.ChokeMessage;
import vertx.bittorrent.messages.HandshakeMessage;
import vertx.bittorrent.messages.HaveMessage;
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
            new ProtocolHandler().onMessage(this::handleProtocolMessage).onInvalidHandshake(v -> close());

    @Getter
    private Bitfield bitfield;

    private boolean handshakeSent = false;
    private boolean handshakeReceived = false;

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
    private int bytesUploaded = 0;

    @Getter
    @Setter
    private int previousBytesUploaded = 0;

    @Getter
    @Setter
    private float downloadRate = 0.0f;
    private long downloadStartedAt = -1;
    private long downloadingDuration = 0;

    private int currentRequestCount = 0;
    private int requestLimit = 6;

    @Getter
    private boolean downloading = false;

    private Map<Integer, PieceState> pieceStates = new HashMap<>();

    private Handler<HandshakeMessage> handshakeHandler;
    private Handler<Bitfield> bitfieldHandler;
    private Handler<Void> chokedHandler;
    private Handler<Void> unchokedHandler;
    private Handler<Void> interestedHandler;
    private Handler<Void> notInterestedHandler;
    private Handler<RequestMessage> requestHandler;
    private Handler<Piece> pieceHandler;
    private Handler<Integer> hasPieceHandler;
    private Handler<Void> closedHandler;

    public PeerConnection(NetSocket socket, ClientState clientState, Peer peer) {
        this.socket = socket;
        this.clientState = clientState;
        this.peer = peer;

        this.bitfield = Bitfield.fromSize((int) clientState.getTorrent().getPiecesCount());

        socket.exceptionHandler(ex -> {
            log.error("[{}] Error", peer, ex);
        });

        socket.handler(buffer -> {
            log.trace("[{}] Received buffer with length {}", peer, buffer.length());
            protocolHandler.readBuffer(buffer);
        });

        socket.closeHandler(v -> {
            log.info("[{}] Peer disconnected", peer);

            if (closedHandler != null) {
                closedHandler.handle(null);
            }
        });
    }

    public double getAverageDownloadRate() {
        double duration = getDownloadingDuration();

        if (duration == 0.0) {
            return 0.0;
        }

        return bytesDownloaded / duration;
    }

    public double getDownloadingDuration() {
        long duration = downloadingDuration;

        if (downloadStartedAt != -1) {
            duration += (System.currentTimeMillis() - downloadStartedAt);
        }

        return duration / 1000.0;
    }

    public boolean isPieceRequested(int index) {
        return pieceStates.containsKey(index);
    }

    public boolean isHandshakeCompleted() {
        return handshakeSent && handshakeReceived;
    }

    public PeerConnection onHandshake(Handler<HandshakeMessage> handler) {
        handshakeHandler = handler;
        return this;
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

    public PeerConnection onHasPiece(Handler<Integer> handler) {
        hasPieceHandler = handler;
        return this;
    }

    public PeerConnection onRequest(Handler<RequestMessage> handler) {
        requestHandler = handler;
        return this;
    }

    public PeerConnection onPieceCompleted(Handler<Piece> handler) {
        pieceHandler = handler;
        return this;
    }

    public PeerConnection onClosed(Handler<Void> handler) {
        closedHandler = handler;
        return this;
    }

    public Future<Void> close() {
        return socket.close();
    }

    public void setDownloading(boolean downloading) {
        if (downloading && !this.downloading) {
            downloadStartedAt = System.currentTimeMillis();
        }

        if (!downloading && this.downloading) {
            downloadingDuration += (System.currentTimeMillis() - downloadStartedAt);
            downloadStartedAt = -1;
        }

        this.downloading = downloading;
    }

    public void handshake() {
        if (!handshakeSent) {
            handshakeSent = true;
            sendMessage(new HandshakeMessage(0L, clientState.getTorrent().getInfoHash(), clientState.getPeerId()));
        }
    }

    public void bitfield() {
        sendMessage(new BitfieldMessage(clientState.getBitfield()));
    }

    public void choke() {
        if (!choked) {
            choked = true;
            sendMessage(new ChokeMessage());
        }
    }

    public void unchoke() {
        if (choked) {
            choked = false;
            sendMessage(new UnchokeMessage());
        }
    }

    public void interested() {
        if (!interested) {
            interested = true;
            sendMessage(new InterestedMessage());
        }
    }

    public void notInterested() {
        if (interested) {
            interested = false;
            sendMessage(new NotInterestedMessage());
        }
    }

    public void have(int index) {
        sendMessage(new HaveMessage(index));
    }

    public void piece(int index, int begin, Buffer data) {
        sendMessage(new PieceMessage(index, begin, data)).onSuccess(v -> {
            bytesUploaded += data.length();
        });
    }

    public void requestPiece(int pieceIndex) {
        if (!pieceStates.containsKey(pieceIndex)) {
            long pieceLength = clientState.getTorrent().getLengthForPiece(pieceIndex);

            pieceStates.put(pieceIndex, new PieceState(pieceLength));

            processRequests();
        }
    }

    private boolean canRequest() {
        if (!downloading || remoteChoked || pieceStates.isEmpty()) {
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
            pieces:
            for (var entry : pieceStates.entrySet()) {
                int pieceIndex = entry.getKey();
                var pieceState = entry.getValue();

                for (int i = 0; i < pieceState.getBlocksCount(); i++) {
                    if (pieceState.getBlockState(i) == BlockState.Queued) {
                        currentRequestCount++;
                        pieceState.setBlockState(i, BlockState.Requested);
                        sendMessage(new RequestMessage(
                                pieceIndex, pieceState.getBlockOffset(i), pieceState.getBlockSize(i)));
                        break pieces;
                    }
                }
            }
        }
    }

    private void handleProtocolMessage(Message message) {
        log.debug("[{}] Received {}", peer, message);

        if (message instanceof HandshakeMessage handshakeMessage) {
            if (!handshakeReceived) {
                handshakeReceived = true;

                if (handshakeHandler != null) {
                    handshakeHandler.handle(handshakeMessage);
                }
            }
        } else if (message instanceof BitfieldMessage bitfieldMessage) {
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
        } else if (message instanceof HaveMessage haveMessage) {
            int pieceIndex = haveMessage.getPieceIndex();

            bitfield.setPiece(pieceIndex);

            if (hasPieceHandler != null) {
                hasPieceHandler.handle(pieceIndex);
            }
        } else if (message instanceof RequestMessage requestMessage) {
            if (requestHandler != null) {
                requestHandler.handle(requestMessage);
            }
        } else if (message instanceof PieceMessage pieceMessage) {
            bytesDownloaded += pieceMessage.getData().length();

            int pieceIndex = pieceMessage.getPieceIndex();
            int begin = pieceMessage.getBegin();

            PieceState pieceState = pieceStates.get(pieceIndex);

            if (pieceState != null) {
                if (pieceState.getBlockStateByOffset(begin) == BlockState.Requested) {
                    // piece was expected

                    pieceState.getData().setBuffer(begin, pieceMessage.getData());

                    pieceState.setBlockStateByOffset(begin, BlockState.Downloaded);
                    currentRequestCount--;

                    if (pieceState.isCompleted()) {
                        pieceStates.remove(pieceIndex);

                        byte[] hash = HashUtils.sha1(pieceState.getData());
                        ByteBuffer pieceHash = clientState.getTorrent().getHashForPiece(pieceIndex);
                        boolean hashValid = HashUtils.isEqual(hash, pieceHash);

                        Piece piece = Piece.builder()
                                .index(pieceIndex)
                                .data(pieceState.getData())
                                .hash(hash)
                                .hashValid(hashValid)
                                .build();

                        if (pieceHandler != null) {
                            pieceHandler.handle(piece);
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

        log.debug("[{}] Sending {}", peer, message);
        return socket.write(message.toBuffer());
    }

    public static Future<PeerConnection> connect(NetClient client, ClientState clientState, Peer peer) {
        log.debug("[{}] Trying to connect to peer", peer);

        return client.connect(peer.getAddress())
                .onFailure(ex -> log.error("[{}] Could not connect to peer: {}", peer, ex.getMessage()))
                .map(socket -> new PeerConnection(socket, clientState, peer))
                .onSuccess(conn -> log.info("[{}] Connected to peer", peer))
                .onSuccess(conn -> conn.handshake());
    }
}
