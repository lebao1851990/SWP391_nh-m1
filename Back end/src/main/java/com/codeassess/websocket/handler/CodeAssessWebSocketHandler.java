package com.codeassess.websocket.handler;

import com.codeassess.websocket.model.WebSocketMessageIn;
import com.codeassess.websocket.model.WebSocketMessageOut;
import com.codeassess.websocket.security.WebSocketSubscriptionGuard;
import com.codeassess.websocket.service.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
@Slf4j
public class CodeAssessWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketSubscriptionGuard subscriptionGuard;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public CodeAssessWebSocketHandler(WebSocketSessionRegistry sessionRegistry,
                                      WebSocketSubscriptionGuard subscriptionGuard,
                                      ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.subscriptionGuard = subscriptionGuard;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    public CodeAssessWebSocketHandler(WebSocketSessionRegistry sessionRegistry,
                                      WebSocketSubscriptionGuard subscriptionGuard) {
        this(sessionRegistry, subscriptionGuard, new ObjectMapper().findAndRegisterModules());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionRegistry.registerSession(session);

        String userId = (String) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");

        log.info("WebSocket connected: session={}, userId={}, role={}", session.getId(), userId, role);

        WebSocketMessageOut welcome = WebSocketMessageOut.builder()
                .type("CONNECTED")
                .userId(userId)
                .role(role)
                .message("Connected to CodeAssess Real-time WebSocket")
                .build();

        sendDirectMessage(session, welcome);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.trim().isEmpty()) {
            return;
        }

        try {
            WebSocketMessageIn msgIn = objectMapper.readValue(payload, WebSocketMessageIn.class);
            if (msgIn == null || msgIn.getAction() == null) {
                sendDirectMessage(session, WebSocketMessageOut.builder()
                        .type("ERROR")
                        .code("BAD_REQUEST")
                        .message("Missing 'action' field in message")
                        .build());
                return;
            }

            String action = msgIn.getAction().trim().toUpperCase();
            String channel = msgIn.getChannel() != null ? msgIn.getChannel().trim() : null;

            switch (action) {
                case "PING":
                    sendDirectMessage(session, WebSocketMessageOut.builder().type("PONG").build());
                    break;

                case "SUBSCRIBE":
                    handleSubscribe(session, channel);
                    break;

                case "UNSUBSCRIBE":
                    handleUnsubscribe(session, channel);
                    break;

                default:
                    sendDirectMessage(session, WebSocketMessageOut.builder()
                            .type("ERROR")
                            .code("BAD_REQUEST")
                            .message("Unsupported action: " + action)
                            .build());
                    break;
            }
        } catch (Exception e) {
            log.warn("Error handling incoming WebSocket message from session {}: {}", session.getId(), e.getMessage());
            sendDirectMessage(session, WebSocketMessageOut.builder()
                    .type("ERROR")
                    .code("BAD_REQUEST")
                    .message("Invalid message format: " + e.getMessage())
                    .build());
        }
    }

    private void handleSubscribe(WebSocketSession session, String channel) {
        if (channel == null || channel.isEmpty()) {
            sendDirectMessage(session, WebSocketMessageOut.builder()
                    .type("ERROR")
                    .code("BAD_REQUEST")
                    .message("Channel is required for SUBSCRIBE action")
                    .build());
            return;
        }

        String userId = (String) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");

        boolean authorized = subscriptionGuard.isAuthorized(channel, userId, role);
        if (!authorized) {
            log.warn("Subscription rejected: session {} (user {}, role {}) attempted subscribing to unauthorized channel '{}'",
                    session.getId(), userId, role, channel);
            sendDirectMessage(session, WebSocketMessageOut.builder()
                    .type("ERROR")
                    .code("FORBIDDEN")
                    .channel(channel)
                    .message("Subscription to channel '" + channel + "' is forbidden")
                    .build());
            return;
        }

        sessionRegistry.addSubscription(channel, session);
        sendDirectMessage(session, WebSocketMessageOut.builder()
                .type("SUBSCRIBED")
                .channel(channel)
                .message("Subscribed to " + channel)
                .build());
    }

    private void handleUnsubscribe(WebSocketSession session, String channel) {
        if (channel != null && !channel.isEmpty()) {
            sessionRegistry.removeSubscription(channel, session);
            sendDirectMessage(session, WebSocketMessageOut.builder()
                    .type("UNSUBSCRIBED")
                    .channel(channel)
                    .build());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket disconnected: session={}, status={}", session.getId(), status);
        sessionRegistry.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
        sessionRegistry.removeSession(session);
    }

    private void sendDirectMessage(WebSocketSession session, WebSocketMessageOut message) {
        if (!session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.error("Failed sending direct message to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
