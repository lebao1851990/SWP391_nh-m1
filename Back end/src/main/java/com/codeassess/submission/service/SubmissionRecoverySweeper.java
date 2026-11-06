package com.codeassess.submission.service;

import com.codeassess.queue.service.BullMqJobDispatcher;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sweeper guaranteeing that submissions are never permanently stuck in PENDING or expired PROCESSING state.
 * Detects pending submissions older than 60s, verifies archive integrity, and atomically releases expired
 * processing leases before idempotent recovery.
 */
@Service
@Slf4j
public class SubmissionRecoverySweeper {

    private final SubmissionRepository submissionRepository;
    private final BullMqJobDispatcher bullMqJobDispatcher;
    private final InternalGradingService internalGradingService;
    private final SubmissionStorageService submissionStorageService;
    private final SourceHasherService sourceHasherService;
    private final StudentQuestionScoreRepository studentQuestionScoreRepository;
    private final SubmissionPersistenceService submissionPersistenceService;

    public SubmissionRecoverySweeper(SubmissionRepository submissionRepository,
                                    BullMqJobDispatcher bullMqJobDispatcher,
                                    InternalGradingService internalGradingService) {
        this(submissionRepository, bullMqJobDispatcher, internalGradingService, null, null, null, null);
    }

    public SubmissionRecoverySweeper(SubmissionRepository submissionRepository,
                                    BullMqJobDispatcher bullMqJobDispatcher,
                                    InternalGradingService internalGradingService,
                                    SubmissionStorageService submissionStorageService) {
        this(submissionRepository, bullMqJobDispatcher, internalGradingService, submissionStorageService, null, null, null);
    }

    public SubmissionRecoverySweeper(SubmissionRepository submissionRepository,
                                    BullMqJobDispatcher bullMqJobDispatcher,
                                    InternalGradingService internalGradingService,
                                    SubmissionStorageService submissionStorageService,
                                    SourceHasherService sourceHasherService,
                                    StudentQuestionScoreRepository studentQuestionScoreRepository) {
        this(submissionRepository, bullMqJobDispatcher, internalGradingService, submissionStorageService, sourceHasherService, studentQuestionScoreRepository, null);
    }

    @Autowired
    public SubmissionRecoverySweeper(
            SubmissionRepository submissionRepository,
            BullMqJobDispatcher bullMqJobDispatcher,
            InternalGradingService internalGradingService,
            @Autowired(required = false) SubmissionStorageService submissionStorageService,
            @Autowired(required = false) SourceHasherService sourceHasherService,
            @Autowired(required = false) StudentQuestionScoreRepository studentQuestionScoreRepository,
            @Autowired(required = false) SubmissionPersistenceService submissionPersistenceService) {
        this.submissionRepository = submissionRepository;
        this.bullMqJobDispatcher = bullMqJobDispatcher;
        this.internalGradingService = internalGradingService;
        this.submissionStorageService = submissionStorageService;
        this.sourceHasherService = sourceHasherService;
        this.studentQuestionScoreRepository = studentQuestionScoreRepository;
        this.submissionPersistenceService = submissionPersistenceService;
    }

    @Scheduled(fixedDelayString = "${submission.sweeper.interval-ms:60000}", initialDelay = 30000)
    public void recoverPendingSubmissions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pendingCutoff = now.minusSeconds(60);

        // 1. Recover un-dispatched PENDING submissions created > 60s ago
        List<Submission> pendingSubmissions = submissionRepository.findByStatusAndCreatedAtBefore("PENDING", pendingCutoff);

        // 2. Recover PROCESSING submissions whose dedicated lease has expired
        List<Submission> expiredProcessingSubmissions = submissionRepository.findByStatusAndLeaseExpiresAtBefore("PROCESSING", now);

        List<Submission> toRecover = new java.util.ArrayList<>(pendingSubmissions);

        for (Submission sub : expiredProcessingSubmissions) {
            try {
                String oldToken = sub.getClaimToken();
                if (oldToken == null || oldToken.isBlank()) {
                    log.debug("Skipping expired PROCESSING submission {} without claim token", sub.getId());
                    continue;
                }
                int released = submissionRepository.releaseExpiredClaim(sub.getId(), oldToken);
                if (released == 1) {
                    log.warn("SubmissionRecoverySweeper revoked expired claim on stuck PROCESSING submission {}", sub.getId());
                    sub.setStatus("PENDING");
                    sub.setClaimToken(null);
                    sub.setLeaseExpiresAt(null);
                    toRecover.add(sub);
                } else {
                    log.debug("Claim release for submission {} matched 0 rows (already completed or recovered by another instance)", sub.getId());
                }
            } catch (Exception e) {
                log.error("Failed to release expired claim for submission {}: {}", sub.getId(), e.getMessage(), e);
            }
        }

        if (toRecover.isEmpty()) {
            return;
        }

        log.info("SubmissionRecoverySweeper detected {} pending/expired submissions requiring recovery", toRecover.size());

        for (Submission sub : toRecover) {
            // A submission with a successfully recorded dispatch must not be redispatched
            if ("DISPATCHED".equalsIgnoreCase(sub.getExecutionStatus())) {
                log.debug("Submission {} is already DISPATCHED and waiting in BullMQ; skipping redispatch", sub.getId());
                continue;
            }

            String recoveryToken = "recovery-" + java.util.UUID.randomUUID();
            LocalDateTime recoveryLease = LocalDateTime.now().plusSeconds(60);

            // Atomic database-level recovery claim:
            // Conditional on s.status = 'PENDING' AND (s.executionStatus IS NULL OR s.executionStatus != 'DISPATCHED')
            // AND (s.claimToken IS NULL OR s.leaseExpiresAt < :now)
            // Exactly one sweeper invocation can acquire this lease.
            int claimed = submissionRepository.claimSubmissionForRecovery(
                    sub.getId(), recoveryToken, recoveryLease, LocalDateTime.now()
            );

            if (claimed == 0) {
                log.debug("Sweeper could not acquire recovery claim for submission {} (already claimed or ineligible)", sub.getId());
                continue;
            }

            try {
                String subType = sub.getSubmissionType() != null ? sub.getSubmissionType() : "SINGLE_FILE";
                String examId = sub.getExam() != null ? sub.getExam().getId() : "";
                String questionId = sub.getQuestion() != null ? sub.getQuestion().getId() : null;
                String studentId = sub.getStudent() != null ? sub.getStudent().getId() : null;

                // For ZIP submissions, verify archive existence, readability, and SHA-256 integrity before dispatch
                if ("ZIP".equalsIgnoreCase(subType)) {
                    byte[] zipBytes = null;
                    if (submissionStorageService != null && submissionStorageService.hasZip(sub.getId())) {
                        try {
                            zipBytes = submissionStorageService.getZipBytes(sub.getId());
                        } catch (Exception e) {
                            log.error("SubmissionRecoverySweeper: Archive for submission {} cannot be read: {}", sub.getId(), e.getMessage());
                            failSubmissionAndSyncScore(sub, recoveryToken, "STORAGE_FAILED", "Submission archive is unreadable: " + e.getMessage(), questionId, studentId);
                            continue;
                        }
                    } else if (sub.getZipStoragePath() != null && !sub.getZipStoragePath().isBlank()) {
                        try {
                            java.nio.file.Path p = java.nio.file.Paths.get(sub.getZipStoragePath());
                            if (java.nio.file.Files.exists(p)) {
                                zipBytes = java.nio.file.Files.readAllBytes(p);
                            }
                        } catch (Exception e) {
                            log.error("SubmissionRecoverySweeper: Archive for submission {} cannot be read from path: {}", sub.getId(), e.getMessage());
                            failSubmissionAndSyncScore(sub, recoveryToken, "STORAGE_FAILED", "Submission archive is unreadable: " + e.getMessage(), questionId, studentId);
                            continue;
                        }
                    }

                    if (zipBytes == null) {
                        log.error("SubmissionRecoverySweeper: Archive missing on disk for submission {}. Marking as FAILED.", sub.getId());
                        failSubmissionAndSyncScore(sub, recoveryToken, "STORAGE_FAILED", "Submission archive missing from storage; grading aborted. Please resubmit.", questionId, studentId);
                        continue;
                    }

                    // Verify archive SHA-256 hash if metadata is present
                    String expectedHash = sub.getArchiveHash();
                    if (expectedHash != null && !expectedHash.isBlank()) {
                        String actualHash;
                        try {
                            if (sourceHasherService != null) {
                                actualHash = sourceHasherService.calculateArchiveSha256(zipBytes);
                            } else {
                                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                                byte[] digest = md.digest(zipBytes);
                                StringBuilder sb = new StringBuilder(digest.length * 2);
                                for (byte b : digest) {
                                    sb.append(String.format("%02x", b));
                                }
                                actualHash = sb.toString();
                            }
                        } catch (Exception hashEx) {
                            log.error("SubmissionRecoverySweeper: Failed to calculate archive hash for {}: {}", sub.getId(), hashEx.getMessage());
                            failSubmissionAndSyncScore(sub, recoveryToken, "STORAGE_FAILED", "Failed to compute archive integrity hash: " + hashEx.getMessage(), questionId, studentId);
                            continue;
                        }

                        if (!expectedHash.equalsIgnoreCase(actualHash)) {
                            log.error("SubmissionRecoverySweeper: Archive hash mismatch for submission {}! Expected: {}, Actual: {}",
                                    sub.getId(), expectedHash, actualHash);
                            failSubmissionAndSyncScore(sub, recoveryToken, "STORAGE_FAILED",
                                    "Submission archive integrity check failed (archive hash mismatch). Please resubmit.", questionId, studentId);
                            continue;
                        }
                    }
                }

                boolean dispatched = bullMqJobDispatcher != null && bullMqJobDispatcher.dispatchGradingJob(sub.getId(), examId, subType);

                if (dispatched) {
                    submissionRepository.recordSuccessfulRecoveryDispatch(sub.getId(), recoveryToken);
                } else {
                    log.info("BullMQ dispatch unavailable for submission {}; running direct fallback grading", sub.getId());
                    if (internalGradingService != null) {
                        internalGradingService.processGrading(sub.getId());
                    } else {
                        // Release recovery claim only if job is confirmed absent from BullMQ (not ambiguous UNKNOWN)
                        BullMqJobDispatcher.JobQueueStatus queueStatus = (bullMqJobDispatcher != null)
                                ? bullMqJobDispatcher.checkJobStatus(sub.getId())
                                : BullMqJobDispatcher.JobQueueStatus.NOT_FOUND;
                        if (queueStatus != BullMqJobDispatcher.JobQueueStatus.UNKNOWN) {
                            submissionRepository.releaseRecoveryClaim(sub.getId(), recoveryToken);
                        } else {
                            log.warn("Submission {} queue status is UNKNOWN after dispatch timeout; retaining recovery lease until expiration to prevent race", sub.getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to recover submission {}: {}", sub.getId(), e.getMessage(), e);
                try {
                    submissionRepository.releaseRecoveryClaim(sub.getId(), recoveryToken);
                } catch (Exception releaseEx) {
                    log.warn("Failed to release recovery claim for {}: {}", sub.getId(), releaseEx.getMessage());
                }
            }
        }
    }

    private void failSubmissionAndSyncScore(
            Submission sub,
            String claimToken,
            String executionStatus,
            String reason,
            String questionId,
            String studentId) {

        if (submissionPersistenceService != null) {
            boolean failed = submissionPersistenceService.failPendingSubmissionAndSyncScore(
                    sub.getId(), claimToken, executionStatus, reason, questionId, studentId
            );
            if (failed) {
                sub.setStatus("FAILED");
                sub.setExecutionStatus(executionStatus);
                sub.setCompileOutput(reason);
            }
        } else {
            int rows = submissionRepository.failPendingSubmission(
                    sub.getId(), claimToken, executionStatus, reason
            );
            if (rows > 0) {
                sub.setStatus("FAILED");
                sub.setExecutionStatus(executionStatus);
                sub.setCompileOutput(reason);
                if (studentQuestionScoreRepository != null && questionId != null && studentId != null) {
                    studentQuestionScoreRepository.updateFailureStatusIfLatest(
                            questionId, studentId, sub.getId(), "FAILED"
                    );
                }
            }
        }
    }
}
