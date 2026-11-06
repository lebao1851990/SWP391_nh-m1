package com.codeassess.websocket.security;

import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketSubscriptionGuard {

    private final SubmissionRepository submissionRepository;

    /**
     * Authorizes whether a user with given ID and Role is permitted to subscribe to the target channel.
     * Prevents cross-student data leakage and enforces RBAC boundaries.
     */
    public boolean isAuthorized(String channel, String userId, String role) {
        if (channel == null || channel.trim().isEmpty() || userId == null || role == null) {
            return false;
        }

        String cleanChannel = channel.trim();
        String upperRole = role.trim().toUpperCase();

        // 1. ADMIN has universal subscription rights
        if ("ADMIN".equals(upperRole)) {
            return true;
        }

        // 2. User personal channel: "user:{targetUserId}"
        if (cleanChannel.startsWith("user:")) {
            String targetUserId = cleanChannel.substring(5).trim();
            return userId.equalsIgnoreCase(targetUserId);
        }

        // 3. Submission channel: "submission:{submissionId}"
        if (cleanChannel.startsWith("submission:")) {
            String submissionId = cleanChannel.substring(11).trim();

            // Teachers can observe any submission in their courses
            if ("TEACHER".equals(upperRole)) {
                return true;
            }

            // Students can ONLY observe their own submissions
            if ("STUDENT".equals(upperRole)) {
                try {
                    Optional<Submission> subOpt = submissionRepository.findById(submissionId);
                    if (subOpt.isPresent()) {
                        Submission sub = subOpt.get();
                        if (sub.getStudent() != null && userId.equalsIgnoreCase(sub.getStudent().getId())) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error validating submission ownership for channel {}: {}", channel, e.getMessage());
                }
                return false;
            }
        }

        // 4. Exam channel: "exam:{examId}"
        if (cleanChannel.startsWith("exam:")) {
            // Only Teachers and Admins can observe exam-level aggregations
            return "TEACHER".equals(upperRole);
        }

        // 5. Queue telemetry: "queue:bullmq"
        if ("queue:bullmq".equalsIgnoreCase(cleanChannel)) {
            // Only Teachers and Admins can observe worker and queue metrics
            return "TEACHER".equals(upperRole);
        }

        log.warn("Subscription denied: unrecognized or unauthorized channel format '{}' for user '{}' (role: '{}')",
                channel, userId, role);
        return false;
    }
}

