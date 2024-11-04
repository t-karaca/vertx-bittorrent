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
import vertx.bittorrent.messages.KeepAliveMessage;
import vertx.bittorrent.messages.Message;
import vertx.bittorrent.messages.NotInterestedMessage;
import vertx.bittorrent.messages.PieceMessage;
import vertx.bittorrent.messages.RequestMessage;
import vertx.bittorrent.messages.UnchokeMessage;
import vertx.bittorrent.model.Bitfield;
import vertx.bittorrent.model.Peer;
import vertx.bittorrent.utils.HashUtils;

@Slf4j
public class PeerConnection {

    private final long connectedAt = System.currentTimeMillis();

    private final NetSocket socket;
    private final ClientState clientState;
    private final TorrentState torrentState;

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
    private long lastMessageSentAt;

    @Getter
    private long lastMessageReceivedAt;

    private long unchokedAt = -1;
    private long remoteUnchokedAt = -1;

    private long requestedAt = -1;
    private long requestingDuration = 0;

    private long unchokedDuration = 0;
    private long remoteUnchokedDuration = 0;

    private long interestedAt = -1;
    private long remoteInterestedAt = -1;

    private long waitingDuration = 0;
    private long remoteWaitingDuration = 0;

    @Getter
    private long downloadedPieces = 0;

    @Getter
    private long faultyPieces = 0;

    private int currentRequestCount = 0;
    private int requestLimit = 12;

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

    public PeerConnection(NetSocket socket, ClientState clientState, TorrentState torrentState, Peer peer) {
        this.socket = socket;
        this.clientState = clientState;
        this.torrentState = torrentState;
        this.peer = peer;

        this.bitfield = Bitfield.fromSize((int) torrentState.getTorrent().getPiecesCount());

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

    /**
     * How long the peer has been connected to in milliseconds
     *
     * @return connection duration in milliseconds
     */
    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectedAt;
    }

    public int getRequestedPiecesCount() {
        return pieceStates.size();
    }

    public double getUnchokedDuration() {
        long duration = unchokedDuration;

        if (unchokedAt != -1) {
            duration += (System.currentTimeMillis() - unchokedAt);
        }

        // in seconds
        return duration / 1000.0;
    }

    public double getRemoteUnchokedDuration() {
        long duration = remoteUnchokedDuration;

        if (remoteUnchokedAt != -1) {
            duration += (System.currentTimeMillis() - remoteUnchokedAt);
        }

        // in seconds
        return duration / 1000.0;
    }

    public double getRequestingDuration() {
        long duration = requestingDuration;

        if (requestedAt != -1) {
            duration += (System.currentTimeMillis() - requestedAt);
        }

        // in seconds
        return duration / 1000.0;
    }

    public double getAverageDownloadRate() {
        double duration = getRequestingDuration();

        if (duration == 0.0) {
            return 0.0;
        }

        return bytesDownloaded / duration;
    }

    public double getAverageUploadRate() {
        double duration = getUnchokedDuration();

        if (duration == 0.0) {
            return 0.0;
        }

        return bytesUploaded / duration;
    }

    public double getCurrentWaitingDuration() {
        if (interestedAt == -1) {
            return 0;
        }

        return (System.currentTimeMillis() - interestedAt) / 1000.0;
    }

    public double getCurrentRemoteWaitingDuration() {
        if (remoteInterestedAt == -1) {
            return 0;
        }

        return (System.currentTimeMillis() - remoteInterestedAt) / 1000.0;
    }

    public double getTotalWaitingDuration() {
        return waitingDuration / 1000.0 + getCurrentWaitingDuration();
    }

    public double getTotalRemoteWaitingDuration() {
        return remoteWaitingDuration / 1000.0 + getCurrentRemoteWaitingDuration();
    }

    public double getDurationSinceLastMessageSent() {
        return (System.currentTimeMillis() - lastMessageSentAt) / 1000.0;
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

    public void handshake() {
        if (!handshakeSent) {
            handshakeSent = true;
            sendMessage(new HandshakeMessage(0L, torrentState.getTorrent().getInfoHash(), clientState.getPeerId()));
        }
    }

    public void keepAlive() {
        sendMessage(new KeepAliveMessage());
    }

    public void bitfield() {
        sendMessage(new BitfieldMessage(torrentState.getBitfield()));
    }

    public void choke() {
        if (!choked) {
            if (unchokedAt != -1) {
                unchokedDuration += (System.currentTimeMillis() - unchokedAt);
            }

            choked = true;

            if (remoteInterested) {
                remoteInterestedAt = System.currentTimeMillis();
            }

            sendMessage(new ChokeMessage());
        }
    }

    public void unchoke() {
        if (choked) {
            if (unchokedAt == -1) {
                unchokedAt = System.currentTimeMillis();
            }

            if (remoteInterestedAt == -1) {
                remoteWaitingDuration = (System.currentTimeMillis() - remoteInterestedAt);
            }

            remoteInterestedAt = -1;

            choked = false;
            sendMessage(new UnchokeMessage());
        }
    }

    public void interested() {
        if (!interested) {
            interestedAt = System.currentTimeMillis();
            interested = true;
            sendMessage(new InterestedMessage());
        }
    }

    public void notInterested() {
        if (interested) {
            if (interestedAt != -1) {
                waitingDuration += (System.currentTimeMillis() - interestedAt);
            }

            interestedAt = -1;
            interested = false;

            sendMessage(new NotInterestedMessage());
        }
    }

    public void have(int index) {
        sendMessage(new HaveMessage(index));
    }

    public Future<Void> piece(int index, int begin, Buffer data) {
        return sendMessage(new PieceMessage(index, begin, data)).onSuccess(v -> {
            bytesUploaded += data.length();
        });
    }

    public void requestPiece(int pieceIndex) {
        if (!pieceStates.containsKey(pieceIndex)) {
            long pieceLength = torrentState.getTorrent().getLengthForPiece(pieceIndex);

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

                        if (requestedAt == -1) {
                            requestedAt = System.currentTimeMillis();
                        }

                        break pieces;
                    }
                }
            }
        }
    }

    private void handleProtocolMessage(Message message) {
        lastMessageReceivedAt = System.currentTimeMillis();

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
            if (remoteUnchokedAt != -1) {
                remoteUnchokedDuration += (System.currentTimeMillis() - remoteUnchokedAt);
                remoteUnchokedAt = -1;
            }

            remoteChoked = true;

            if (interested) {
                interestedAt = System.currentTimeMillis();
            }

            if (chokedHandler != null) {
                chokedHandler.handle(null);
            }
        } else if (message instanceof UnchokeMessage) {
            if (interestedAt != -1) {
                waitingDuration += (System.currentTimeMillis() - interestedAt);
            }

            if (remoteUnchokedAt == -1) {
                remoteUnchokedAt = System.currentTimeMillis();
            }

            remoteChoked = false;

            interestedAt = -1;

            if (unchokedHandler != null) {
                unchokedHandler.handle(null);
            }
        } else if (message instanceof InterestedMessage) {
            remoteInterested = true;

            remoteInterestedAt = System.currentTimeMillis();

            if (interestedHandler != null) {
                interestedHandler.handle(null);
            }
        } else if (message instanceof NotInterestedMessage) {
            if (remoteInterestedAt != -1) {
                remoteWaitingDuration += (System.currentTimeMillis() - remoteInterestedAt);
            }

            remoteInterested = false;

            remoteInterestedAt = -1;

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

                    if (currentRequestCount <= 0 && requestedAt != -1) {
                        requestingDuration += (System.currentTimeMillis() - requestedAt);
                        requestedAt = -1;
                    }

                    if (pieceState.isCompleted()) {
                        downloadedPieces++;

                        pieceStates.remove(pieceIndex);

                        byte[] hash = HashUtils.sha1(pieceState.getData());
                        ByteBuffer pieceHash = torrentState.getTorrent().getHashForPiece(pieceIndex);
                        boolean hashValid = HashUtils.isEqual(hash, pieceHash);

                        Piece piece = Piece.builder()
                                .index(pieceIndex)
                                .data(pieceState.getData())
                                .hash(hash)
                                .hashValid(hashValid)
                                .build();

                        if (!hashValid) {
                            faultyPieces++;
                        }

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
        return socket.write(message.toBuffer()).onSuccess(v -> {
            lastMessageSentAt = System.currentTimeMillis();
        });
    }

    public static Future<PeerConnection> connect(
            NetClient client, ClientState clientState, TorrentState torrentState, Peer peer) {
        log.debug("[{}] Trying to connect to peer", peer);

        return client.connect(peer.getAddress())
                .onFailure(ex -> log.error("[{}] Could not connect to peer: {}", peer, ex.getMessage()))
                .map(socket -> new PeerConnection(socket, clientState, torrentState, peer))
                .onSuccess(conn -> log.info("[{}] Connected to peer", peer))
                .onSuccess(conn -> conn.handshake());
    }
}
