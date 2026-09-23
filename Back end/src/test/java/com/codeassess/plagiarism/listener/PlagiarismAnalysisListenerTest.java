package com.codeassess.plagiarism.listener;

import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

/**
 * Unit tests for PlagiarismAnalysisListener.
 *
 * Verifies:
 * F. Failure isolation — AST failure does not propagate outward.
 * G. Event pipeline — correct call order (fingerprints → matrix).
 * H. Failure invariant — no submission mutation on AST failure.
 */
class PlagiarismAnalysisListenerTest {

    private AstPlagiarismService astPlagiarismService;
    private SimilarityMatrixService similarityMatrixService;
    private PlagiarismAnalysisListener listener;

    @BeforeEach
    void setUp() {
        astPlagiarismService = Mockito.mock(AstPlagiarismService.class);
        similarityMatrixService = Mockito.mock(SimilarityMatrixService.class);
        listener = new PlagiarismAnalysisListener(astPlagiarismService, similarityMatrixService);
    }

    // ===================================================================
    // G. Event pipeline — correct service call order
    // ===================================================================

    @Test
    void handleSubmissionCreated_callsServicesInOrder() {
        SubmissionCreatedEvent event = new SubmissionCreatedEvent(this, "exam-1", "sub-1");

        listener.handleSubmissionCreated(event);

        InOrder inOrder = Mockito.inOrder(astPlagiarismService, similarityMatrixService);
        inOrder.verify(astPlagiarismService).generateFingerprintsForSubmission("sub-1");
        inOrder.verify(similarityMatrixService).generateMatrixForExam("exam-1");
    }

    @Test
    void handleSubmissionCreated_passesCorrectIds() {
        SubmissionCreatedEvent event = new SubmissionCreatedEvent(this, "exam-abc", "sub-xyz");

        listener.handleSubmissionCreated(event);

        verify(astPlagiarismService, times(1)).generateFingerprintsForSubmission("sub-xyz");
        verify(similarityMatrixService, times(1)).generateMatrixForExam("exam-abc");
    }

    // ===================================================================
    // F. Failure isolation — AST fingerprint failure
    // ===================================================================

    @Test
    void handleSubmissionCreated_astFingerprintFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Parser crashed")).when(astPlagiarismService)
                .generateFingerprintsForSubmission(anyString());

        SubmissionCreatedEvent event = new SubmissionCreatedEvent(this, "exam-1", "sub-1");

        // Must NOT throw. The listener must swallow the exception to protect
        // the already-committed Submission from being affected.
        listener.handleSubmissionCreated(event);

        // No similarity matrix should run if fingerprinting failed
        verify(similarityMatrixService, never()).generateMatrixForExam(anyString());
    }

    @Test
    void handleSubmissionCreated_matrixFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Matrix calculation failed")).when(similarityMatrixService)
                .generateMatrixForExam(anyString());

        SubmissionCreatedEvent event = new SubmissionCreatedEvent(this, "exam-1", "sub-1");

        // Must NOT throw. Submission remains safe.
        listener.handleSubmissionCreated(event);

        // Fingerprinting did run before matrix failed
        verify(astPlagiarismService, times(1)).generateFingerprintsForSubmission("sub-1");
    }

    @Test
    void handleSubmissionCreated_nullSourceCode_doesNotPropagate() {
        // AstPlagiarismService.generateFingerprintsForSubmission silently returns
        // when sourceCode is null/empty — but if it throws, listener must still be safe.
        doThrow(new IllegalArgumentException("Source code is empty")).when(astPlagiarismService)
                .generateFingerprintsForSubmission(anyString());

        SubmissionCreatedEvent event = new SubmissionCreatedEvent(this, "exam-1", "sub-empty");

        listener.handleSubmissionCreated(event);

        // No side effects leaked
        verify(similarityMatrixService, never()).generateMatrixForExam(anyString());
    }

    // ===================================================================
    // G. Correct annotation contract — AFTER_COMMIT + @Async
    // ===================================================================

    @Test
    void listener_hasTransactionalEventListenerAnnotation() throws NoSuchMethodException {
        var method = PlagiarismAnalysisListener.class.getMethod("handleSubmissionCreated", SubmissionCreatedEvent.class);

        var tel = method.getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class);
        assert tel != null : "Missing @TransactionalEventListener";

        assert tel.phase() == org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT
                : "Phase must be AFTER_COMMIT but was: " + tel.phase();

        var async = method.getAnnotation(org.springframework.scheduling.annotation.Async.class);
        assert async != null : "Missing @Async annotation";
    }
}
