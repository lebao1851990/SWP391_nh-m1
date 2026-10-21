package com.codeassess.plagiarism.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.ExamMatrixCoordination;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.exception.StaleWorkerGenerationException;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.ExamMatrixCoordinationRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.plagiarism.similarity.SimilarityResult;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarityMatrixService {

    private final ExamRepository examRepository;
    private final SubmissionRepository submissionRepository;
    private final AstFingerprintRepository fingerprintRepository;
    private final SimilarityPairRepository similarityPairRepository;
    private final PlagiarismEvidenceRepository evidenceRepository;
    private final JaccardSimilarityCalculator similarityCalculator;
    private final EvidenceGenerator evidenceGenerator;
    private final ExamMatrixCoordinationRepository coordinationRepository;
    private final PlatformTransactionManager transactionManager;

    public record LeaseGrant(String examId, Long lockedGeneration, String leaseToken) {}

    public record PairUpdatePlan(
            Long pairId,
            BigDecimal similarityRate,
            Integer sharedHashesCount,
            Boolean isFlagged,
            List<AstFingerprint> fpA,
            List<AstFingerprint> fpB
    ) {}

    public record PairInsertPlan(
            Submission subA,
            Submission subB,
            BigDecimal similarityRate,
            Integer sharedHashesCount,
            Boolean isFlagged,
            List<AstFingerprint> fpA,
            List<AstFingerprint> fpB
    ) {}

    public record MatrixCalculationPlan(
            Exam exam,
            List<PairUpdatePlan> updates,
            List<PairInsertPlan> inserts,
            List<Long> pairsToDeactivate
    ) {}

    private String canonicalKey(String id1, String id2) {
        return id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
    }

    private <T> T executeInTransaction(TransactionCallback<T> action) {
        if (transactionManager != null) {
            return new TransactionTemplate(transactionManager).execute(action);
        } else {
            return action.doInTransaction(null);
        }
    }

    private void executeWithoutResult(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action) {
        if (transactionManager != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(action::accept);
        } else {
            action.accept(null);
        }
    }

    /**
     * Atomically requests a matrix refresh for an exam by incrementing requested_generation.
     */
    public void requestRefresh(String examId) {
        coordinationRepository.requestMatrixRefresh(examId);
    }

    /**
     * Phase 1: Lease Acquisition.
     * Executes in a short transaction T1. Acquires pessimistic lock on exam_matrix_coordination,
     * checks if work is pending, and assigns a monotonic lease token.
     */
    public Optional<LeaseGrant> acquireLease(String examId, String workerId) {
        return executeInTransaction(status -> {
            coordinationRepository.initCoordinationIfAbsent(examId);

            ExamMatrixCoordination coord = coordinationRepository.findByExamIdForUpdate(examId)
                    .orElseThrow(() -> new IllegalStateException("Coordination row not found for exam: " + examId));

            // Check if exam is in FAILED status (circuit breaker until new refresh requested)
            if ("FAILED".equalsIgnoreCase(coord.getStatus())) {
                log.warn("Matrix refresh for exam {} is in FAILED status (retries exhausted). Skipping until reactivated by new refresh request.", examId);
                return Optional.empty();
            }

            // Check if exam is in retry backoff
            if (coord.getNextRetryAt() != null && LocalDateTime.now().isBefore(coord.getNextRetryAt())) {
                log.info("Matrix refresh for exam {} is in retry backoff until {}. Skipping lease acquisition.",
                        examId, coord.getNextRetryAt());
                return Optional.empty();
            }

            // Check if an active, unexpired lease exists
            if ("RUNNING".equalsIgnoreCase(coord.getStatus())
                    && coord.getLeaseExpiresAt() != null
                    && LocalDateTime.now().isBefore(coord.getLeaseExpiresAt())) {
                log.info("Matrix refresh for exam {} is currently running under valid lease until {}",
                        examId, coord.getLeaseExpiresAt());
                return Optional.empty();
            }

            // Check if there is pending work
            if (coord.getRequestedGeneration() <= coord.getCurrentGeneration()) {
                log.info("Matrix for exam {} is already up to date (requested={}, current={})",
                        examId, coord.getRequestedGeneration(), coord.getCurrentGeneration());
                return Optional.empty();
            }

            // Grant lease
            long lockedGen = coord.getRequestedGeneration();
            String leaseToken = UUID.randomUUID().toString();

            coord.setLockedGeneration(lockedGen);
            coord.setLeaseToken(leaseToken);
            coord.setWorkerId(workerId != null ? workerId : "worker-" + UUID.randomUUID().toString().substring(0, 8));
            coord.setLeaseExpiresAt(LocalDateTime.now().plusSeconds(120));
            coord.setStatus("RUNNING");
            coordinationRepository.save(coord);

            return Optional.of(new LeaseGrant(examId, lockedGen, leaseToken));
        });
    }

    /**
     * Safely extends an active lease with fencing-token validation.
     * Prevents stale, expired, or superseded workers from renewing leases.
     *
     * @param examId the exam ID
     * @param leaseToken the worker's lease token
     * @param extensionSeconds seconds to extend lease
     * @return true if renewed successfully; false if superseded, expired, or not running
     */
    public boolean renewLease(String examId, String leaseToken, int extensionSeconds) {
        return executeInTransaction(status -> {
            Optional<ExamMatrixCoordination> coordOpt = coordinationRepository.findByExamIdForUpdate(examId);
            if (coordOpt.isEmpty()) {
                return false;
            }
            ExamMatrixCoordination coord = coordOpt.get();

            if (!"RUNNING".equalsIgnoreCase(coord.getStatus())) {
                log.warn("Cannot renew lease for exam {}: status is {}", examId, coord.getStatus());
                return false;
            }

            if (leaseToken == null || !leaseToken.equals(coord.getLeaseToken())) {
                log.warn("Cannot renew lease for exam {}: superseded or invalid lease token", examId);
                return false;
            }

            if (coord.getLeaseExpiresAt() == null || !LocalDateTime.now().isBefore(coord.getLeaseExpiresAt())) {
                log.warn("Cannot renew lease for exam {}: lease already expired at {}", examId, coord.getLeaseExpiresAt());
                return false;
            }

            coord.setLeaseExpiresAt(LocalDateTime.now().plusSeconds(extensionSeconds));
            coordinationRepository.save(coord);
            log.info("Successfully renewed lease for exam {} by {}s until {}", examId, extensionSeconds, coord.getLeaseExpiresAt());
            return true;
        });
    }

    /**
     * Phase 2: Similarity Calculation.
     * Executes outside database transaction lock to avoid holding DB connections during CPU work.
     */
    public MatrixCalculationPlan computeCalculation(String examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));

        List<Submission> submissions = submissionRepository.findByExam_Id(examId);
        List<SimilarityPair> existingPairs = similarityPairRepository.findByExam_Id(examId);

        Map<String, SimilarityPair> existingMap = new HashMap<>(existingPairs.size());
        for (SimilarityPair p : existingPairs) {
            existingMap.put(canonicalKey(p.getSubmissionA().getId(), p.getSubmissionB().getId()), p);
        }

        Map<String, List<AstFingerprint>> fingerprintsBySub = new HashMap<>(submissions.size());
        for (Submission sub : submissions) {
            fingerprintsBySub.put(sub.getId(), fingerprintRepository.findBySubmission_Id(sub.getId()));
        }

        Set<String> computedKeys = new HashSet<>();
        List<PairUpdatePlan> updates = new ArrayList<>();
        List<PairInsertPlan> inserts = new ArrayList<>();

        for (int i = 0; i < submissions.size(); i++) {
            for (int j = i + 1; j < submissions.size(); j++) {
                Submission sub1 = submissions.get(i);
                Submission sub2 = submissions.get(j);

                // Exclude self-comparison if student resubmitted multiple times
                if (sub1.getStudent() != null && sub2.getStudent() != null
                        && sub1.getStudent().getId().equals(sub2.getStudent().getId())) {
                    continue;
                }

                Submission subA = sub1.getId().compareTo(sub2.getId()) < 0 ? sub1 : sub2;
                Submission subB = sub1.getId().compareTo(sub2.getId()) < 0 ? sub2 : sub1;

                String key = subA.getId() + ":" + subB.getId();
                computedKeys.add(key);

                List<AstFingerprint> fpA = fingerprintsBySub.getOrDefault(subA.getId(), Collections.emptyList());
                List<AstFingerprint> fpB = fingerprintsBySub.getOrDefault(subB.getId(), Collections.emptyList());

                Set<Long> hashesA = fpA.stream().map(AstFingerprint::getHashValue).collect(Collectors.toSet());
                Set<Long> hashesB = fpB.stream().map(AstFingerprint::getHashValue).collect(Collectors.toSet());

                SimilarityResult result = similarityCalculator.calculate(hashesA, hashesB);

                SimilarityPair existing = existingMap.get(key);
                if (existing != null) {
                    updates.add(new PairUpdatePlan(
                            existing.getId(),
                            result.getSimilarityRate(),
                            result.getSharedHashesCount(),
                            result.isFlagged(),
                            fpA,
                            fpB
                    ));
                } else {
                    inserts.add(new PairInsertPlan(
                            subA,
                            subB,
                            result.getSimilarityRate(),
                            result.getSharedHashesCount(),
                            result.isFlagged(),
                            fpA,
                            fpB
                    ));
                }
            }
        }

        List<Long> pairsToDeactivate = new ArrayList<>();
        for (SimilarityPair existing : existingPairs) {
            String key = canonicalKey(existing.getSubmissionA().getId(), existing.getSubmissionB().getId());
            if (!computedKeys.contains(key) && Boolean.TRUE.equals(existing.getIsActive())) {
                pairsToDeactivate.add(existing.getId());
            }
        }

        return new MatrixCalculationPlan(exam, updates, inserts, pairsToDeactivate);
    }

    /**
     * Phase 3: Fenced Commit & Reconciliation.
     * Executes in a short write transaction T2. Validates fencing token and generation,
     * updates pairs in place, replaces evidence for updated pairs, soft-deactivates
     * removed pairs, and advances committed generation.
     */
    public void commitReconciliation(String examId, Long lockedGen, String leaseToken, MatrixCalculationPlan plan) {
        executeWithoutResult(status -> {
            ExamMatrixCoordination coord = coordinationRepository.findByExamIdForUpdate(examId)
                    .orElseThrow(() -> new IllegalStateException("Coordination row not found for exam: " + examId));

            boolean isTokenValid = leaseToken != null && leaseToken.equals(coord.getLeaseToken());
            boolean isGenValid = lockedGen != null && lockedGen.equals(coord.getLockedGeneration());
            boolean isNotExpired = coord.getLeaseExpiresAt() != null && LocalDateTime.now().isBefore(coord.getLeaseExpiresAt());

            if (!isTokenValid || !isGenValid || !isNotExpired) {
                throw new StaleWorkerGenerationException(
                        String.format("Fencing check failed for exam %s (tokenValid=%s, genValid=%s, notExpired=%s)",
                                examId, isTokenValid, isGenValid, isNotExpired)
                );
            }

            // 1. Update existing pairs in place (preserving primary keys and adjudication fields)
            for (PairUpdatePlan update : plan.updates()) {
                similarityPairRepository.updateCalculatedMetrics(
                        update.pairId(),
                        update.similarityRate(),
                        update.sharedHashesCount(),
                        update.isFlagged()
                );

                evidenceRepository.deleteBySimilarityPair_Id(update.pairId());
                evidenceRepository.flush();

                if (update.sharedHashesCount() > 0) {
                    SimilarityPair pairRef = similarityPairRepository.getReferenceById(update.pairId());
                    List<PlagiarismEvidence> evidences = evidenceGenerator.generateEvidence(pairRef, update.fpA(), update.fpB());
                    evidenceRepository.saveAll(evidences);
                }
            }

            // 2. Insert new pairs
            for (PairInsertPlan insert : plan.inserts()) {
                SimilarityPair newPair = SimilarityPair.builder()
                        .exam(plan.exam())
                        .submissionA(insert.subA())
                        .submissionB(insert.subB())
                        .similarityRate(insert.similarityRate())
                        .sharedHashesCount(insert.sharedHashesCount())
                        .isFlagged(insert.isFlagged())
                        .adjudicationStatus("PENDING")
                        .isActive(true)
                        .build();

                newPair = similarityPairRepository.save(newPair);

                if (insert.sharedHashesCount() > 0) {
                    List<PlagiarismEvidence> evidences = evidenceGenerator.generateEvidence(newPair, insert.fpA(), insert.fpB());
                    evidenceRepository.saveAll(evidences);
                }
            }

            // 3. Soft-deactivate pairs no longer present (evidence retained for audit)
            if (!plan.pairsToDeactivate().isEmpty()) {
                similarityPairRepository.deactivatePairsByIds(plan.pairsToDeactivate());
            }

            // 4. Advance committed generation and release lease
            coord.setCurrentGeneration(lockedGen);
            coord.setStatus("IDLE");
            coord.setLeaseToken(null);
            coord.setWorkerId(null);
            coord.setLeaseExpiresAt(null);
            coord.setFailureCount(0);
            coord.setLastError(null);
            coord.setNextRetryAt(null);
            coordinationRepository.save(coord);
        });
    }

    public void handleCalculationFailure(String examId, String leaseToken) {
        handleCalculationFailure(examId, leaseToken, null);
    }

    public void handleCalculationFailure(String examId, String leaseToken, Throwable error) {
        if (transactionManager != null) {
            TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(status -> recordCalculationFailure(examId, leaseToken, error));
        } else {
            recordCalculationFailure(examId, leaseToken, error);
        }
    }

    private void recordCalculationFailure(String examId, String leaseToken, Throwable error) {
        coordinationRepository.findByExamIdForUpdate(examId).ifPresent(c -> {
            if (leaseToken != null && leaseToken.equals(c.getLeaseToken())) {
                int failures = (c.getFailureCount() != null ? c.getFailureCount() : 0) + 1;
                c.setFailureCount(failures);
                String errorMsg = error != null ? error.getClass().getSimpleName() + ": " + error.getMessage() : "Unknown calculation error";
                if (errorMsg.length() > 950) {
                    errorMsg = errorMsg.substring(0, 950);
                }
                c.setLastError(errorMsg);
                c.setLeaseToken(null);
                c.setLeaseExpiresAt(null);
                c.setWorkerId(null);

                if (failures >= 3) {
                    c.setStatus("FAILED");
                    c.setNextRetryAt(null);
                    log.error("Matrix calculation for exam {} marked as FAILED after {} consecutive failures: {}",
                            examId, failures, errorMsg);
                } else {
                    int backoffSeconds = (int) Math.min(300, Math.pow(2, failures) * 10);
                    c.setStatus("IDLE");
                    c.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                    log.warn("Matrix calculation for exam {} failed (attempt {}). Backing off for {}s until {}: {}",
                            examId, failures, backoffSeconds, c.getNextRetryAt(), errorMsg);
                }
                coordinationRepository.save(c);
            }
        });
    }

    /**
     * Standard entrypoint for refreshing an exam similarity matrix.
     * Implements full generation coalescing, lease fencing, and in-place reconciliation.
     */
    public void generateMatrixForExam(String examId) {
        generateMatrixForExam(examId, "worker-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @FunctionalInterface
    public interface LeaseHeartbeat extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Starts a background lease renewal heartbeat for long calculation workloads.
     * Safely closes and shuts down executor on completion or failure to prevent thread leaks.
     */
    public LeaseHeartbeat startLeaseHeartbeat(String examId, String leaseToken, long intervalSeconds, int extensionSeconds) {
        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-heartbeat-" + examId);
            t.setDaemon(true);
            return t;
        });

        java.util.concurrent.ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean renewed = renewLease(examId, leaseToken, extensionSeconds);
                if (!renewed) {
                    log.warn("Heartbeat failed to renew lease for exam {}: lease superseded or expired", examId);
                }
            } catch (Exception ex) {
                log.error("Exception in heartbeat renewal for exam {}: {}", examId, ex.getMessage());
            }
        }, intervalSeconds, intervalSeconds, java.util.concurrent.TimeUnit.SECONDS);

        return () -> {
            try {
                future.cancel(true);
            } finally {
                scheduler.shutdownNow();
            }
        };
    }

    public void generateMatrixForExam(String examId, String workerId) {
        Optional<LeaseGrant> leaseOpt = acquireLease(examId, workerId);
        if (leaseOpt.isEmpty()) {
            return;
        }

        LeaseGrant lease = leaseOpt.get();
        try (LeaseHeartbeat heartbeat = startLeaseHeartbeat(examId, lease.leaseToken(), 30, 120)) {
            MatrixCalculationPlan plan = computeCalculation(examId);
            commitReconciliation(examId, lease.lockedGeneration(), lease.leaseToken(), plan);
        } catch (StaleWorkerGenerationException e) {
            log.warn("Matrix calculation for exam {} rejected by fencing: {}", examId, e.getMessage());
        } catch (Exception e) {
            handleCalculationFailure(examId, lease.leaseToken(), e);
            throw e;
        }
    }
}
