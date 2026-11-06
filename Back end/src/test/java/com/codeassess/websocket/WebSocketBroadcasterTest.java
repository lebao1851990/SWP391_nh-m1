package com.codeassess.websocket;

import com.codeassess.websocket.model.WebSocketEvent;
import com.codeassess.websocket.service.WebSocketBroadcaster;
import com.codeassess.websocket.service.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebSocketBroadcasterTest {

    private WebSocketSessionRegistry registry;
    private WebSocketBroadcaster broadcaster;

    @Mock
    private WebSocketSession sessionAlice;

    @Mock
    private WebSocketSession sessionBob;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
        broadcaster = new WebSocketBroadcaster(registry);

        lenient().when(sessionAlice.getId()).thenReturn("sess-alice");
        lenient().when(sessionBob.getId()).thenReturn("sess-bob");
        lenient().when(sessionAlice.isOpen()).thenReturn(true);
        lenient().when(sessionBob.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("Broadcast delivers only to sessions subscribed to that specific channel")
    void testChannelTargetedBroadcast() throws Exception {
        registry.addSubscription("submission:sub-101", sessionAlice);
        registry.addSubscription("submission:sub-202", sessionBob);

        WebSocketEvent event = WebSocketEvent.builder()
                .channel("submission:sub-101")
                .event("SUBMISSION_STATUS_UPDATED")
                .payload(Map.of("status", "COMPLETED", "score", 95.0))
                .build();

        int delivered = broadcaster.broadcast(event);

        assertEquals(1, delivered, "Event should be delivered only to Alice's session");
        verify(sessionAlice, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionBob, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Convenience broadcastSubmissionStatus delivers to both submission and student channels")
    void testBroadcastSubmissionStatus() throws Exception {
        registry.addSubscription("submission:sub-999", sessionAlice);
        registry.addSubscription("user:student-777", sessionBob);

        broadcaster.broadcastSubmissionStatus("sub-999", "exam-1", "student-777", "COMPLETED", "SUCCESS", 100.0, 100.0);

        verify(sessionAlice, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionBob, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Closed sessions are safely evicted during broadcast failure")
    void testClosedSessionEviction() throws Exception {
        when(sessionAlice.isOpen()).thenReturn(false);
        registry.addSubscription("exam:exam-1", sessionAlice);

        WebSocketEvent event = WebSocketEvent.builder()
                .channel("exam:exam-1")
                .event("PLAGIARISM_MATRIX_UPDATED")
                .payload(Map.of("pairsAnalyzed", 12))
                .build();

        int delivered = broadcaster.broadcast(event);
        assertEquals(0, delivered);
        assertFalse(registry.isSessionSubscribed("exam:exam-1", sessionAlice), "Closed session must be evicted");
    }
}

