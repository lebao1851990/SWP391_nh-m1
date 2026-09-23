package com.codeassess.plagiarism.listener;

import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PlagiarismAnalysisListener {

    private static final Logger logger = LoggerFactory.getLogger(PlagiarismAnalysisListener.class);

    private final AstPlagiarismService astPlagiarismService;
    private final SimilarityMatrixService similarityMatrixService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubmissionCreated(SubmissionCreatedEvent event) {
        String submissionId = event.getSubmissionId();
        String examId = event.getExamId();
        
        logger.info("Starting background plagiarism analysis for submission: {}", submissionId);

        try {
            // 1. Generate AST Fingerprints
            astPlagiarismService.generateFingerprintsForSubmission(submissionId);
            
            // 2. Generate Similarity Matrix & Evidence
            similarityMatrixService.generateMatrixForExam(examId);
            
            logger.info("Successfully completed plagiarism analysis for submission: {}", submissionId);
        } catch (Exception e) {
            // Log the error but DO NOT throw it back to the event publisher.
            // This isolates the failure so it does not affect the already-created Submission.
            logger.error("Failed to process plagiarism analysis for submission {}: {}", submissionId, e.getMessage(), e);
        }
    }
}
