package com.codeassess.queue.service;

import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.queue.dto.JobType;
import com.codeassess.queue.dto.PlagiarismJobPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * BullMQ / Redis Stream worker component for processing matrix refresh jobs.
 * Enforces database coordination authoritativeness and idempotent execution.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatrixRefreshWorker {

    private final SimilarityMatrixService similarityMatrixService;

    /**
     * Processes a plagiarism/matrix job payload from the queue.
     * Database coordination remains authoritative: duplicate delivery is safely skipped,
     * stale generations are fenced off, and failure counts are tracked in MySQL.
     *
     * @param payload the job payload
     * @return true if job was processed or safely skipped as up to date; false if invalid
     */
    public boolean processJob(PlagiarismJobPayload payload) {
        if (payload == null || payload.getExamId() == null || payload.getExamId().trim().isEmpty()) {
            log.warn("Received null or invalid job payload; ignoring");
            return false;
        }

        if (payload.getJobType() != JobType.MATRIX_REFRESH && payload.getJobType() != JobType.PLAGIARISM_ANALYSIS) {
            log.debug("Skipping unhandled job type: {}", payload.getJobType());
            return false;
        }

        String examId = payload.getExamId();
        String workerId = "bullmq-worker-" + payload.getJobId();
        log.info("BullMQ worker processing matrix refresh for exam {} (jobId: {})", examId, payload.getJobId());

        try {
            similarityMatrixService.generateMatrixForExam(examId, workerId);
            return true;
        } catch (Exception e) {
            log.error("BullMQ worker execution failed for exam {} (jobId: {}): {}",
                    examId, payload.getJobId(), e.getMessage());
            throw e;
        }
    }
}
