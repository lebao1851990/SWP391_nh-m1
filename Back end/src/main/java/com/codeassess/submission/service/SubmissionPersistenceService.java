package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionPersistenceService.class);

    private final SubmissionRepository submissionRepository;
    private final AstPlagiarismService astPlagiarismService;
    private final ApplicationEventPublisher eventPublisher;

    public SubmissionPersistenceService(
            SubmissionRepository submissionRepository,
            AstPlagiarismService astPlagiarismService,
            ApplicationEventPublisher eventPublisher) {
        this.submissionRepository = submissionRepository;
        this.astPlagiarismService = astPlagiarismService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Persists the initial ZIP submission record with status PENDING and execution_status IN_PROGRESS.
     * Runs in its own transaction and commits immediately, releasing database locks and connections
     * before any long-running Docker operations begin.
     */
    @Transactional
    public Submission createInitialZipSubmission(
            Exam exam,
            User student,
            String originalFilename,
            String archiveHash,
            String sourceTreeHash,
            int fileCount,
            String entryPointClass) {

        Submission submission = Submission.builder()
                .exam(exam)
                .student(student)
                .status("PENDING")
                .submissionType("ZIP")
                .originalFilename(originalFilename)
                .archiveHash(archiveHash)
                .sourceTreeHash(sourceTreeHash)
                .fileCount(fileCount)
                .entryPointClass(entryPointClass)
                .executionStatus("IN_PROGRESS")
                .build();

        return submissionRepository.save(submission);
    }

    /**
     * Updates execution telemetry, exit code, compiler output, and generates multi-file AST fingerprints.
     * Runs in a separate short transaction after Docker container execution has terminated.
     */
    public static final int MAX_OUTPUT_BYTES = 65_535;
    public static final String TRUNCATION_MARKER = "\n... [TRUNCATED]";
    private static final byte[] TRUNCATION_MARKER_BYTES = TRUNCATION_MARKER.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Transactional
    public Submission updateExecutionTelemetryAndFingerprints(
            String submissionId,
            String examId,
            SandboxResult sandboxResult,
            ExtractedWorkspace workspace) {

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));

        String executionStatus = sandboxResult.getStatus() != null
                ? sandboxResult.getStatus().name()
                : "INTERNAL_ERROR";

        submission.setExecutionStatus(executionStatus);
        submission.setExitCode(sandboxResult.getExitCode());
        submission.setCompileOutput(safeTruncateUtf8(sandboxResult.getCompilerOutput(), MAX_OUTPUT_BYTES));
        submission.setStdout(safeTruncateUtf8(sandboxResult.getStdout(), MAX_OUTPUT_BYTES));
        submission.setStderr(safeTruncateUtf8(sandboxResult.getStderr(), MAX_OUTPUT_BYTES));
        submission.setExecutionDurationMs(sandboxResult.getExecutionTimeMs());

        submission = submissionRepository.save(submission);

        // Generate AST fingerprints for each valid Java file in workspace
        try {
            astPlagiarismService.generateFingerprintsForWorkspace(submission, workspace);
        } catch (Exception e) {
            log.error("Failed to generate AST fingerprints for workspace submission {}: {}", submissionId, e.getMessage(), e);
        }

        // Publish event for similarity matrix processing
        eventPublisher.publishEvent(new SubmissionCreatedEvent(this, examId, submission.getId()));

        return submission;
    }

    /**
     * Safely bounds output text to at most {@code maxBytes} when encoded in UTF-8.
     * Guarantees that multibyte UTF-8 characters and UTF-16 surrogate pairs are never split mid-character.
     * Appends a truncation marker if truncation was required and budget permits.
     */
    public static String safeTruncateUtf8(String text, int maxBytes) {
        if (text == null || text.isEmpty() || maxBytes <= 0) {
            return text == null ? null : "";
        }

        byte[] rawBytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (rawBytes.length <= maxBytes) {
            return text;
        }

        boolean canFitMarker = maxBytes >= TRUNCATION_MARKER_BYTES.length;
        int budget = canFitMarker ? maxBytes - TRUNCATION_MARKER_BYTES.length : maxBytes;

        int byteCount = 0;
        int lastValidCharIndex = 0;
        int length = text.length();

        for (int i = 0; i < length; ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            int utf8BytesForCodePoint;
            if (codePoint <= 0x7F) {
                utf8BytesForCodePoint = 1;
            } else if (codePoint <= 0x7FF) {
                utf8BytesForCodePoint = 2;
            } else if (codePoint <= 0xFFFF) {
                utf8BytesForCodePoint = 3;
            } else {
                utf8BytesForCodePoint = 4;
            }

            if (byteCount + utf8BytesForCodePoint > budget) {
                break;
            }

            byteCount += utf8BytesForCodePoint;
            i += charCount;
            lastValidCharIndex = i;
        }

        String prefix = text.substring(0, lastValidCharIndex);
        return canFitMarker ? prefix + TRUNCATION_MARKER : prefix;
    }

    /**
     * Backward-compatible alias for safeTruncateUtf8.
     */
    public static String safeTruncate(String text, int maxBytes) {
        return safeTruncateUtf8(text, maxBytes);
    }
}

