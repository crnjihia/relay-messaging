package com.relay.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.dto.MarkReadRequest;
import com.relay.dto.SendMessageRequest;
import com.relay.repository.ConversationRepository;
import com.relay.security.JwtService;
import com.relay.service.MessageService;
import com.relay.service.PresenceService;
import com.relay.service.ReadReceiptService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RelayWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketHandler.class);

    private final JwtService jwtService;
    private final StompSessionRegistry sessionRegistry;
    private final MessageService messageService;
    private final ReadReceiptService readReceiptService;
    private final PresenceService presenceService;
    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    public RelayWebSocketHandler(JwtService jwtService,
                                 StompSessionRegistry sessionRegistry,
                                 MessageService messageService,
                                 ReadReceiptService readReceiptService,
                                 PresenceService presenceService,
                                 ConversationRepository conversationRepository,
                                 ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.sessionRegistry = sessionRegistry;
        this.messageService = messageService;
        this.readReceiptService = readReceiptService;
        this.presenceService = presenceService;
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        AtomicReference<StompSession> stompSessionRef = new AtomicReference<>();

        // Output pipeline: frames emitted to this session are converted to WebSocket messages
        Mono<Void> output = session.send(
                Flux.defer(() -> {
                    StompSession s = stompSessionRef.get();
                    if (s != null) {
                        return s.getFrameStream();
                    }
                    return Flux.empty();
                }).map(frame -> session.textMessage(StompFrameEncoder.encode(frame)))
        );

        // Input pipeline: messages from client are decoded into STOMP frames and processed
        Mono<Void> input = session.receive()
                .filter(msg -> msg.getType() == WebSocketMessage.Type.TEXT)
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    List<StompFrame> frames = StompFrameDecoder.decode(payload);
                    return Flux.fromIterable(frames);
                })
                .flatMap(frame -> handleStompFrame(session, stompSessionRef, frame))
                .then();

        // Run both input and output concurrently until connection terminates
        return Mono.zip(input, output).then()
                .doFinally(signalType -> {
                    StompSession activeSession = stompSessionRef.get();
                    if (activeSession != null) {
                        sessionRegistry.unregister(sessionId);
                        String userId = activeSession.getUserId();
                        // If user has no more active sessions locally, mark away with grace period
                        if (!sessionRegistry.isUserLocallyConnected(userId)) {
                            presenceService.markAwayOnDisconnect(userId).subscribe();
                        }
                    }
                    log.info("WebSocket connection closed for session: {}", sessionId);
                });
    }

    private Mono<Void> handleStompFrame(WebSocketSession session,
                                        AtomicReference<StompSession> sessionRef,
                                        StompFrame frame) {
        StompCommand cmd = frame.command();

        if (cmd == StompCommand.CONNECT || cmd == StompCommand.STOMP) {
            return handleConnect(session, sessionRef, frame);
        }

        StompSession stompSession = sessionRef.get();
        if (stompSession == null) {
            log.warn("Unauthorized STOMP command {} before CONNECT on session {}", cmd, session.getId());
            return sendErrorAndClose(session, "Must CONNECT before sending commands", null);
        }

        return switch (cmd) {
            case SUBSCRIBE -> handleSubscribe(stompSession, frame);
            case UNSUBSCRIBE -> handleUnsubscribe(stompSession, frame);
            case SEND -> handleSend(stompSession, frame);
            case DISCONNECT -> handleDisconnect(stompSession, frame);
            default -> {
                log.debug("Unhandled STOMP command: {}", cmd);
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleConnect(WebSocketSession session,
                                     AtomicReference<StompSession> sessionRef,
                                     StompFrame frame) {
        String token = frame.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        // Query param fallback (?token=...)
        if (token == null || token.isBlank()) {
            URI uri = session.getHandshakeInfo().getUri();
            token = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
        }

        if (token == null || token.isBlank() || !jwtService.isValid(token)) {
            log.warn("STOMP CONNECT rejected: missing or invalid JWT on session {}", session.getId());
            return sendErrorAndClose(session, "Authentication failed: Missing or invalid JWT token in Authorization header", null);
        }

        Claims claims = jwtService.parseAndValidate(token);
        String userId = claims.getSubject();
        String username = (String) claims.get("username");

        StompSession stompSession = new StompSession(session.getId(), userId, username, session);
        sessionRef.set(stompSession);
        sessionRegistry.register(stompSession);

        // Send CONNECTED frame
        StompFrame connectedFrame = StompFrame.builder(StompCommand.CONNECTED)
                .header("version", "1.2")
                .header("user-name", username)
                .header("session", session.getId())
                .header("heart-beat", "10000,10000")
                .build();

        stompSession.emit(connectedFrame, true);

        // Broadcast ONLINE presence
        return presenceService.markOnline(userId);
    }

    private Mono<Void> handleSubscribe(StompSession session, StompFrame frame) {
        String destination = frame.getHeader("destination");
        String subId = frame.getHeader("id");

        if (destination == null || destination.isBlank() || subId == null || subId.isBlank()) {
            return sendSessionError(session, "SUBSCRIBE frame requires 'destination' and 'id' headers");
        }

        // Conversation topic subscription: /topic/conversations/{conversationId}
        if (destination.startsWith("/topic/conversations/")) {
            String conversationId = destination.substring("/topic/conversations/".length());
            return conversationRepository.findById(conversationId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation does not exist")))
                    .flatMap(conv -> {
                        if (!conv.getMemberIds().contains(session.getUserId())) {
                            return sendSessionError(session, "Access denied: Not a member of conversation " + conversationId);
                        }
                        session.addSubscription(subId, destination);
                        sendReceiptIfRequested(session, frame);
                        return Mono.empty();
                    })
                    .onErrorResume(e -> sendSessionError(session, e.getMessage()));
        }

        // User destination subscriptions:
        // /user/queue/messages, /user/queue/receipts, /user/queue/presence
        if (destination.startsWith("/user/queue/")) {
            session.addSubscription(subId, destination);
            sendReceiptIfRequested(session, frame);
            return Mono.empty();
        }

        // Other destinations
        session.addSubscription(subId, destination);
        sendReceiptIfRequested(session, frame);
        return Mono.empty();
    }

    private Mono<Void> handleUnsubscribe(StompSession session, StompFrame frame) {
        String subId = frame.getHeader("id");
        if (subId != null) {
            session.removeSubscription(subId);
            sendReceiptIfRequested(session, frame);
        }
        return Mono.empty();
    }

    private Mono<Void> handleSend(StompSession session, StompFrame frame) {
        String destination = frame.getHeader("destination");
        if (destination == null) {
            return sendSessionError(session, "SEND frame requires 'destination' header");
        }

        return switch (destination) {
            case "/app/chat.send" -> handleChatSend(session, frame);
            case "/app/chat.read" -> handleChatRead(session, frame);
            case "/app/presence.heartbeat" -> handlePresenceHeartbeat(session, frame);
            default -> sendSessionError(session, "Unknown destination: " + destination);
        };
    }

    private Mono<Void> handleChatSend(StompSession session, StompFrame frame) {
        try {
            SendMessageRequest request = objectMapper.readValue(frame.body(), SendMessageRequest.class);
            String conversationId = request.conversationId();
            if (conversationId == null || conversationId.isBlank()) {
                return sendSessionError(session, "Missing conversationId in SendMessageRequest payload");
            }

            return messageService.sendMessage(session.getUserId(), conversationId, request)
                    .doOnSuccess(msg -> sendReceiptIfRequested(session, frame))
                    .then()
                    .onErrorResume(err -> sendSessionError(session, "Failed to send message: " + err.getMessage()));
        } catch (Exception e) {
            return sendSessionError(session, "Malformed SendMessageRequest JSON: " + e.getMessage());
        }
    }

    private Mono<Void> handleChatRead(StompSession session, StompFrame frame) {
        try {
            // Can be MarkReadRequest or include conversationId in payload
            MarkReadRequest request = objectMapper.readValue(frame.body(), MarkReadRequest.class);
            String conversationId = frame.getHeader("conversationId");
            if (conversationId == null) {
                // Try to read from JSON if not in header
                var jsonNode = objectMapper.readTree(frame.body());
                if (jsonNode.has("conversationId")) {
                    conversationId = jsonNode.get("conversationId").asText();
                }
            }

            if (conversationId == null || conversationId.isBlank()) {
                return sendSessionError(session, "Missing conversationId header or property for mark read");
            }

            return readReceiptService.markRead(session.getUserId(), conversationId, request.messageId())
                    .doOnSuccess(rcpt -> sendReceiptIfRequested(session, frame))
                    .then()
                    .onErrorResume(err -> sendSessionError(session, "Failed to mark read: " + err.getMessage()));
        } catch (Exception e) {
            return sendSessionError(session, "Malformed MarkReadRequest JSON: " + e.getMessage());
        }
    }

    private Mono<Void> handlePresenceHeartbeat(StompSession session, StompFrame frame) {
        return presenceService.recordHeartbeat(session.getUserId())
                .doOnSuccess(v -> sendReceiptIfRequested(session, frame));
    }

    private Mono<Void> handleDisconnect(StompSession session, StompFrame frame) {
        sendReceiptIfRequested(session, frame);
        session.close(CloseStatus.NORMAL);
        return presenceService.markAwayOnDisconnect(session.getUserId());
    }

    private void sendReceiptIfRequested(StompSession session, StompFrame frame) {
        String receiptId = frame.getHeader("receipt");
        if (receiptId != null && !receiptId.isBlank()) {
            StompFrame receiptFrame = StompFrame.builder(StompCommand.RECEIPT)
                    .header("receipt-id", receiptId)
                    .build();
            session.emit(receiptFrame, true);
        }
    }

    private Mono<Void> sendSessionError(StompSession session, String message) {
        StompFrame errorFrame = StompFrame.builder(StompCommand.ERROR)
                .header("message", message)
                .body(message)
                .build();
        session.emit(errorFrame, true);
        return Mono.empty();
    }

    private Mono<Void> sendErrorAndClose(WebSocketSession session, String message, String details) {
        StompFrame.Builder builder = StompFrame.builder(StompCommand.ERROR)
                .header("message", message);
        if (details != null) {
            builder.body(details);
        }
        String encoded = StompFrameEncoder.encode(builder.build());
        return session.send(Mono.just(session.textMessage(encoded)))
                .then(session.close(CloseStatus.POLICY_VIOLATION));
    }
}
