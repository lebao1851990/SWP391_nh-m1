package com.codeassess.websocket.service;

import com.codeassess.websocket.model.WebSocketEvent;
import com.codeassess.websocket.model.WebSocketMessageOut;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class WebSocketBroadcaster {

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public WebSocketBroadcaster(WebSocketSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    public WebSocketBroadcaster(WebSocketSessionRegistry registry) {
        this(registry, new ObjectMapper().findAndRegisterModules());
    }

    /**
     * Broadcasts an event to all authorized active subscribers on the specified channel.
     */
    public int broadcast(WebSocketEvent event) {
        if (event == null || event.getChannel() == null) {
            return 0;
        }

        Set<WebSocketSession> subscribers = registry.getSubscribers(event.getChannel());
        if (subscribers.isEmpty()) {
            log.debug("No active subscribers for channel: {}", event.getChannel());
            return 0;
        }

        WebSocketMessageOut outMessage = WebSocketMessageOut.builder()
                .type("EVENT")
                .channel(event.getChannel())
                .event(event)
                .build();

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(outMessage);
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket event payload for channel {}: {}", event.getChannel(), e.getMessage());
            return 0;
        }

        TextMessage textMessage = new TextMessage(payloadJson);
        int deliveredCount = 0;

        for (WebSocketSession session : subscribers) {
            if (!session.isOpen()) {
                registry.removeSession(session);
                continue;
            }

            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                        deliveredCount++;
                    }
                }
            } catch (IOException e) {
                log.warn("Failed sending message to session {}: {}. Evicting session.", session.getId(), e.getMessage());
                registry.removeSession(session);
            }
        }

        log.info("Broadcast event '{}' to channel '{}' (delivered to {} sessions)",
                event.getEvent(), event.getChannel(), deliveredCount);
        return deliveredCount;
    }

    /**
     * Convenience method: broadcasts submission status updates to both submission and student channels.
     */
    public void broadcastSubmissionStatus(String submissionId,
                                          String examId,
                                          String studentId,
                                          String status,
                                          String executionStatus,
                                          Double score,
                                          Double totalPoints) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submissionId);
        payload.put("examId", examId);
        payload.put("status", status);
        payload.put("executionStatus", executionStatus != null ? executionStatus : "UNKNOWN");
        if (score != null) payload.put("score", score);
        if (totalPoints != null) payload.put("totalPoints", totalPoints);

        // 1. Broadcast to submission-specific channel (for details page & grading view)
        WebSocketEvent subEvent = WebSocketEvent.builder()
                .eventId("evt_" + UUID.randomUUID())
                .timestamp(Instant.now())
                .version("1.0")
                .channel("submission:" + submissionId)
                .event("SUBMISSION_STATUS_UPDATED")
                .payload(payload)
                .build();
        broadcast(subEvent);

        // 2. Broadcast to student user channel (for student submissions list & notification pill)
        if (studentId != null && !studentId.trim().isEmpty()) {
            WebSocketEvent userEvent = WebSocketEvent.builder()
                    .eventId("evt_" + UUID.randomUUID())
                    .timestamp(Instant.now())
                    .version("1.0")
                    .channel("user:" + studentId.trim())
                    .event("SUBMISSION_STATUS_UPDATED")
                    .payload(payload)
                    .build();
            broadcast(userEvent);
        }
    }

    /**
     * Convenience method: broadcasts plagiarism matrix computation completion.
     */
    public void broadcastPlagiarismMatrixUpdated(String examId, int pairsAnalyzed) {
        if (examId == null) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("examId", examId);
        payload.put("pairsAnalyzed", pairsAnalyzed);

        WebSocketEvent event = WebSocketEvent.builder()
                .eventId("evt_" + UUID.randomUUID())
                .timestamp(Instant.now())
                .version("1.0")
                .channel("exam:" + examId)
                .event("PLAGIARISM_MATRIX_UPDATED")
                .payload(payload)
                .build();
        broadcast(event);
    }

    /**
     * Convenience method: broadcasts appeal status update.
     */
    public void broadcastAppealStatus(String appealId, String studentId, String status, String remarks) {
        if (studentId == null) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appealId", appealId);
        payload.put("status", status);
        if (remarks != null) payload.put("remarks", remarks);

        WebSocketEvent event = WebSocketEvent.builder()
                .eventId("evt_" + UUID.randomUUID())
                .timestamp(Instant.now())
                .version("1.0")
                .channel("user:" + studentId)
                .event("APPEAL_STATUS_UPDATED")
                .payload(payload)
                .build();
        broadcast(event);
    }

    /**
     * Convenience method: broadcasts queue progress telemetry.
     */
    public void broadcastQueueProgress(String queueName, int active, int completed, int failed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queueName", queueName);
        payload.put("activeJobs", active);
        payload.put("completedJobs", completed);
        payload.put("failedJobs", failed);

        WebSocketEvent event = WebSocketEvent.builder()
                .eventId("evt_" + UUID.randomUUID())
                .timestamp(Instant.now())
                .version("1.0")
                .channel("queue:bullmq")
                .event("QUEUE_PROGRESS")
                .payload(payload)
                .build();
        broadcast(event);
    }
}
