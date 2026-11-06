package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SubmissionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionPersistenceService.class);

    private final SubmissionRepository submissionRepository;
    private final AstPlagiarismService astPlagiarismService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private ExamRepository examRepository;

    @Autowired(required = false)
    private ExamQuestionRepository examQuestionRepository;

    @Autowired(required = false)
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    public SubmissionPersistenceService(
            SubmissionRepository submissionRepository,
            AstPlagiarismService astPlagiarismService,
            ApplicationEventPublisher eventPublisher) {
        this(submissionRepository, astPlagiarismService, eventPublisher, null, null, null);
    }

    @Autowired
    public SubmissionPersistenceService(
            SubmissionRepository submissionRepository,
            AstPlagiarismService astPlagiarismService,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) ExamRepository examRepository,
            @Autowired(required = false) ExamQuestionRepository examQuestionRepository,
            @Autowired(required = false) StudentQuestionScoreRepository studentQuestionScoreRepository) {
        this.submissionRepository = submissionRepository;
        this.astPlagiarismService = astPlagiarismService;
        this.eventPublisher = eventPublisher;
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.studentQuestionScoreRepository = studentQuestionScoreRepository;
    }

    /**
     * Resolves the target ExamQuestion under the approved lock hierarchy within the active transaction:
     * Level 1: exams (FOR SHARE / read check)
     * Level 2: exam_questions (FOR UPDATE / PESSIMISTIC_WRITE for portable TiDB & MySQL row locking)
     */
    private ExamQuestion resolveAndLockQuestionInTx(String examId, String questionId) {
        if (examQuestionRepository == null) {
            return null;
        }

        if (questionId != null && !questionId.isBlank()) {
            ExamQuestion question = examQuestionRepository.findByIdAndExam_IdForUpdate(questionId.trim(), examId)
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found or does not belong to this exam: " + questionId));

            if ("DRAFT".equalsIgnoreCase(question.getStatus())) {
                throw new IllegalStateException("Question is not currently published for submissions: " + questionId);
            }
            return question;
        }

        // Single-question legacy fallback: resolve the first/default question for this exam
        List<ExamQuestion> questions = examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId);
        if (questions.isEmpty()) {
            return null;
        }

        ExamQuestion defaultQuestion = questions.get(0);
        return examQuestionRepository.findByIdAndExam_IdForUpdate(defaultQuestion.getId(), examId).orElse(defaultQuestion);
    }

    /**
     * Legacy signature preserving single-question backwards compatibility.
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
        return createInitialZipSubmission(exam, null, student, originalFilename, archiveHash, sourceTreeHash, fileCount, entryPointClass);
    }

    /**
     * Overloaded method accepting pre-resolved Question (or null).
     */
    @Transactional
    public Submission createInitialZipSubmission(
            Exam exam,
            ExamQuestion question,
            User student,
            String originalFilename,
            String archiveHash,
            String sourceTreeHash,
            int fileCount,
            String entryPointClass) {
        return createInitialZipSubmission(exam != null ? exam.getId() : null,
                question != null ? question.getId() : null,
                exam,
                question,
                student,
                originalFilename,
                archiveHash,
                sourceTreeHash,
                fileCount,
                entryPointClass);
    }

    /**
     * Persists the initial ZIP submission record with status PENDING and execution_status IN_PROGRESS,
     * maintaining the strict lock hierarchy and atomic score upsert in the SAME physical transaction.
     * Locks are held strictly during the database write transaction and released immediately upon commit,
     * keeping Docker execution entirely outside database locks.
     */
    @Transactional
    public Submission createInitialZipSubmission(
            String examId,
            String questionId,
            Exam fallbackExam,
            ExamQuestion fallbackQuestion,
            User student,
            String originalFilename,
            String archiveHash,
            String sourceTreeHash,
            int fileCount,
            String entryPointClass) {

        Exam lockedExam = fallbackExam;
        if (examRepository != null && examId != null) {
            lockedExam = examRepository.findByIdForShare(examId).orElse(fallbackExam);
        }

        // Level 2 Lock: exam_questions FOR SHARE inside the transaction
        ExamQuestion lockedQuestion = fallbackQuestion;
        if (examId != null) {
            lockedQuestion = resolveAndLockQuestionInTx(examId, questionId);
        }

        Submission submission = Submission.builder()
                .exam(lockedExam)
                .question(lockedQuestion)
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

        submission = submissionRepository.save(submission);

        // Level 3 Lock: Atomic score sequence upsert inside the same transaction
        if (studentQuestionScoreRepository != null && lockedQuestion != null && lockedExam != null) {
            String scoreId = UUID.randomUUID().toString();
            studentQuestionScoreRepository.upsertIntakeSequence(
                    scoreId,
                    lockedExam.getId(),
                    lockedQuestion.getId(),
                    student.getId(),
                    submission.getId()
            );
        }

        return submission;
    }

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

        if (astPlagiarismService != null && workspace != null) {
            try {
                astPlagiarismService.generateFingerprintsForWorkspace(submission, workspace);
            } catch (Exception e) {
                log.error("Failed to generate AST fingerprints for submission {}: {}", submissionId, e.getMessage(), e);
            }
        }

        eventPublisher.publishEvent(new SubmissionCreatedEvent(this, examId, submission.getId()));

        return submission;
    }

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

    public static String safeTruncate(String text, int maxBytes) {
        return safeTruncateUtf8(text, maxBytes);
    }

    /**
     * Atomically and conditionally transitions an eligible PENDING submission to FAILED
     * and synchronizes student_question_scores.latest_status in the same transaction.
     *
     * <p>Invariants:
     * 1. Fails the submission ONLY IF it is still in PENDING state and either unleased or leased by the caller token.
     * 2. If a worker has concurrently transitioned the submission to PROCESSING or COMPLETED, affected rows = 0
     *    and student_question_scores is NOT modified.
     * 3. Syncs student_question_scores ONLY IF latest_submission_id matches the failing submission.
     * 4. Guarantees transactional consistency: submission status and score status commit or roll back together.
     *
     * @return true if the submission was conditionally transitioned to FAILED; false if already claimed/completed.
     */
    @Transactional
    public boolean failPendingSubmissionAndSyncScore(
            String submissionId,
            String claimToken,
            String executionStatus,
            String compileOutput,
            String questionId,
            String studentId) {

        int rows = submissionRepository.failPendingSubmission(
                submissionId,
                claimToken,
                executionStatus,
                compileOutput
        );

        if (rows == 0) {
            log.warn("Submission {} was no longer eligible for recovery failure (already claimed, processing, or completed by worker)", submissionId);
            return false;
        }

        if (studentQuestionScoreRepository != null && questionId != null && studentId != null) {
            int scoreRows = studentQuestionScoreRepository.updateFailureStatusIfLatest(
                    questionId,
                    studentId,
                    submissionId,
                    "FAILED"
            );
            log.info("Conditionally transitioned submission {} to FAILED; student_question_scores updated (rows={})",
                    submissionId, scoreRows);
        }

        return true;
    }
}
