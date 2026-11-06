package com.codeassess.websocket;

import com.codeassess.websocket.handler.CodeAssessWebSocketHandler;
import com.codeassess.websocket.security.WebSocketSubscriptionGuard;
import com.codeassess.websocket.service.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CodeAssessWebSocketHandlerTest {

    private WebSocketSessionRegistry registry;

    @Mock
    private WebSocketSubscriptionGuard subscriptionGuard;

    @Mock
    private WebSocketSession session;

    private CodeAssessWebSocketHandler handler;
    private Map<String, Object> sessionAttributes;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
        handler = new CodeAssessWebSocketHandler(registry, subscriptionGuard);

        sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", "student-alice");
        sessionAttributes.put("role", "STUDENT");

        lenient().when(session.getId()).thenReturn("sess-test-001");
        lenient().when(session.getAttributes()).thenReturn(sessionAttributes);
        lenient().when(session.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("Connection establishment registers session and sends CONNECTED message")
    void testConnectionEstablished() throws Exception {
        handler.afterConnectionEstablished(session);

        assertEquals(1, registry.getActiveSessionCount());

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();

        assertTrue(payload.contains("\"type\":\"CONNECTED\""));
        assertTrue(payload.contains("\"userId\":\"student-alice\""));
    }

    @Test
    @DisplayName("PING action receives PONG response")
    void testPingPong() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"action\":\"PING\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"type\":\"PONG\""));
    }

    @Test
    @DisplayName("SUBSCRIBE action registers channel when authorized")
    void testSubscribeAuthorized() throws Exception {
        when(subscriptionGuard.isAuthorized("submission:sub-101", "student-alice", "STUDENT")).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage("{\"action\":\"SUBSCRIBE\",\"channel\":\"submission:sub-101\"}"));

        assertTrue(registry.isSessionSubscribed("submission:sub-101", session));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"type\":\"SUBSCRIBED\""));
    }

    @Test
    @DisplayName("SUBSCRIBE action returns ERROR FORBIDDEN when unauthorized")
    void testSubscribeForbidden() throws Exception {
        when(subscriptionGuard.isAuthorized("submission:sub-bob", "student-alice", "STUDENT")).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage("{\"action\":\"SUBSCRIBE\",\"channel\":\"submission:sub-bob\"}"));

        assertFalse(registry.isSessionSubscribed("submission:sub-bob", session));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertTrue(payload.contains("\"type\":\"ERROR\""));
        assertTrue(payload.contains("\"code\":\"FORBIDDEN\""));
    }

    @Test
    @DisplayName("Connection close cleans up session and all associated channel subscriptions")
    void testConnectionClosedCleanup() throws Exception {
        when(subscriptionGuard.isAuthorized("submission:sub-101", "student-alice", "STUDENT")).thenReturn(true);
        handler.handleTextMessage(session, new TextMessage("{\"action\":\"SUBSCRIBE\",\"channel\":\"submission:sub-101\"}"));
        assertEquals(1, registry.getSubscribedChannelCount());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertEquals(0, registry.getActiveSessionCount());
        assertEquals(0, registry.getSubscribedChannelCount());
    }
}

