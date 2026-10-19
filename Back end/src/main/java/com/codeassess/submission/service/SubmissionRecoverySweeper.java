package com.codeassess.submission.service;

import com.codeassess.queue.service.BullMqJobDispatcher;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sweeper guaranteeing that submissions are never permanently stuck in PENDING state.
 * Detects pending submissions older than 60s and retries BullMQ dispatch or processes them directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionRecoverySweeper {

    private final SubmissionRepository submissionRepository;
    private final BullMqJobDispatcher bullMqJobDispatcher;
    private final InternalGradingService internalGradingService;

    @Scheduled(fixedDelayString = "${submission.sweeper.interval-ms:60000}", initialDelay = 30000)
    public void recoverPendingSubmissions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pendingCutoff = now.minusSeconds(60);

        // 1. Recover un-dispatched PENDING submissions created > 60s ago
        List<Submission> pendingSubmissions = submissionRepository.findByStatusAndCreatedAtBefore("PENDING", pendingCutoff);

        // 2. Recover PROCESSING submissions whose dedicated lease has expired
        List<Submission> expiredProcessingSubmissions = submissionRepository.findByStatusAndLeaseExpiresAtBefore("PROCESSING", now);

        for (Submission sub : expiredProcessingSubmissions) {
            try {
                String oldToken = sub.getClaimToken();
                int released = submissionRepository.releaseExpiredClaim(sub.getId(), oldToken);
                if (released > 0) {
                    log.warn("SubmissionRecoverySweeper revoked expired claim on stuck PROCESSING submission {}", sub.getId());
                }
            } catch (Exception e) {
                log.error("Failed to release expired claim for submission {}: {}", sub.getId(), e.getMessage(), e);
            }
        }

        List<Submission> toRecover = new java.util.ArrayList<>(pendingSubmissions);
        toRecover.addAll(expiredProcessingSubmissions);

        if (toRecover.isEmpty()) {
            return;
        }

        log.info("SubmissionRecoverySweeper detected {} pending/expired submissions requiring recovery", toRecover.size());

        for (Submission sub : toRecover) {
            try {
                String subType = sub.getSubmissionType() != null ? sub.getSubmissionType() : "SINGLE_FILE";
                String examId = sub.getExam() != null ? sub.getExam().getId() : "";

                boolean dispatched = bullMqJobDispatcher != null && bullMqJobDispatcher.dispatchGradingJob(sub.getId(), examId, subType);

                if (!dispatched) {
                    log.info("BullMQ dispatch unavailable for submission {}; running direct fallback grading", sub.getId());
                    internalGradingService.processGrading(sub.getId());
                }
            } catch (Exception e) {
                log.error("Failed to recover submission {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }
}
