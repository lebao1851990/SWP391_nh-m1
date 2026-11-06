package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import com.codeassess.submission.exception.ZipValidationException;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(StudentSubmissionService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ZipExtractionService zipExtractionService;
    private final SourceHasherService sourceHasherService;
    private final EntryPointDiscoveryService entryPointDiscoveryService;
    private final DockerSandboxService dockerSandboxService;
    private final AstPlagiarismService astPlagiarismService;
    private final ZipSubmissionProperties zipProperties;
    private final SubmissionPersistenceService submissionPersistenceService;
    private final InternalGradingService internalGradingService;
    private final com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService;
    private final com.codeassess.queue.service.BullMqJobDispatcher bullMqJobDispatcher;

    @Autowired(required = false)
    private ExamQuestionRepository examQuestionRepository;

    @Autowired(required = false)
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Autowired(required = false)
    private SubmissionStorageService submissionStorageService;

    @Autowired(required = false)
    private com.codeassess.plagiarism.service.SimilarityMatrixService similarityMatrixService;

    public StudentSubmissionService(
            SubmissionRepository submissionRepository,
            SubmissionMapper submissionMapper,
            ExamRepository examRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this(submissionRepository, submissionMapper, examRepository, userRepository, eventPublisher,
                null, null, null, null, null, new ZipSubmissionProperties(), null, null, null, null);
    }

    public StudentSubmissionService(
            SubmissionRepository submissionRepository,
            SubmissionMapper submissionMapper,
            ExamRepository examRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            ZipExtractionService zipExtractionService,
            SourceHasherService sourceHasherService,
            EntryPointDiscoveryService entryPointDiscoveryService,
            DockerSandboxService dockerSandboxService,
            AstPlagiarismService astPlagiarismService,
            ZipSubmissionProperties zipProperties,
            SubmissionPersistenceService submissionPersistenceService) {
        this(submissionRepository, submissionMapper, examRepository, userRepository, eventPublisher,
                zipExtractionService, sourceHasherService, entryPointDiscoveryService, dockerSandboxService,
                astPlagiarismService, zipProperties, submissionPersistenceService, null, null, null);
    }

    @Autowired
    public StudentSubmissionService(
            SubmissionRepository submissionRepository,
            SubmissionMapper submissionMapper,
            ExamRepository examRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            ZipExtractionService zipExtractionService,
            SourceHasherService sourceHasherService,
            EntryPointDiscoveryService entryPointDiscoveryService,
            DockerSandboxService dockerSandboxService,
            AstPlagiarismService astPlagiarismService,
            ZipSubmissionProperties zipProperties,
            SubmissionPersistenceService submissionPersistenceService,
            @Autowired(required = false) InternalGradingService internalGradingService,
            @Autowired(required = false) com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService,
            @Autowired(required = false) com.codeassess.queue.service.BullMqJobDispatcher bullMqJobDispatcher) {
        this.submissionRepository = submissionRepository;
        this.submissionMapper = submissionMapper;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.zipExtractionService = zipExtractionService;
        this.sourceHasherService = sourceHasherService;
        this.entryPointDiscoveryService = entryPointDiscoveryService;
        this.dockerSandboxService = dockerSandboxService;
        this.astPlagiarismService = astPlagiarismService;
        this.zipProperties = zipProperties;
        this.submissionPersistenceService = submissionPersistenceService;
        this.internalGradingService = internalGradingService;
        this.automatedTestRunnerService = automatedTestRunnerService;
        this.bullMqJobDispatcher = bullMqJobDispatcher;
    }

    @Transactional(readOnly = true)
    public List<SubmissionListResponse> getStudentSubmissions(String studentId) {
        return submissionRepository.findByStudent_Id(studentId).stream()
                .map(submissionMapper::toStudentListResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubmissionDetailResponse> getStudentExamSubmissions(String examId, String studentId) {
        return submissionRepository.findByExam_IdAndStudent_IdOrderByCreatedAtDesc(examId, studentId).stream()
                .map(submissionMapper::toStudentDetailResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getStudentSubmissionById(String id, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(id, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));
        return submissionMapper.toStudentDetailResponse(submission);
    }

    @Transactional(readOnly = true)
    public byte[] getSubmissionZip(String id, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(id, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));
        if (submissionStorageService == null) {
            throw new ResourceNotFoundException("Storage service not available");
        }
        return submissionStorageService.getZipBytes(submission.getId());
    }

    @Transactional(readOnly = true)
    public List<com.codeassess.submission.dto.response.SubmissionFileDto> getSubmissionFiles(String id, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(id, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));
        if (submissionStorageService == null) {
            return java.util.Collections.emptyList();
        }
        return submissionStorageService.getSubmissionFiles(submission.getId());
    }

    private void validateExamAvailability(Exam exam) {
        if (exam.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Exam not found or access denied");
        }

        String runtimeStatus = exam.getRuntimeStatus();
        if ("upcoming".equalsIgnoreCase(runtimeStatus)) {
            throw new IllegalStateException("Exam has not started yet");
        }
        if (!"active".equalsIgnoreCase(runtimeStatus)) {
            if ("completed".equalsIgnoreCase(runtimeStatus) && exam.getExamDate() != null && exam.getDuration() != null) {
                LocalDateTime deadline = exam.getExamDate().plusMinutes(exam.getDuration());
                if (LocalDateTime.now(Exam.APP_ZONE).isAfter(deadline)) {
                    throw new com.codeassess.common.exception.ExamTimeExpiredException("Exam submission window has expired. Deadline was: " + deadline);
                }
            }
            throw new IllegalStateException("Exam is not currently active for submissions");
        }

        if (exam.getExamDate() != null) {
            LocalDateTime now = LocalDateTime.now(Exam.APP_ZONE);
            // Allow 1-minute grace margin for clock synchronization when exam status is already active
            if (now.plusMinutes(1).isBefore(exam.getExamDate())) {
                throw new IllegalStateException("Exam has not started yet");
            }
            if (exam.getDuration() != null && exam.getDuration() > 0) {
                LocalDateTime deadline = exam.getExamDate().plusMinutes(exam.getDuration());
                if (now.isAfter(deadline)) {
                    throw new com.codeassess.common.exception.ExamTimeExpiredException("Exam submission window has expired. Deadline was: " + deadline);
                }
            }
        }
    }

    /**
     * Resolves the target ExamQuestion under the approved lock hierarchy.
     * Level 1: exams (FOR SHARE / read check)
     * Level 2: exam_questions (FOR UPDATE / PESSIMISTIC_WRITE for portable TiDB & MySQL row locking)
     * Throws ResourceNotFoundException if single-question default cannot be determined.
     */
    private ExamQuestion resolveAndLockQuestion(String examId, String questionId) {
        if (examQuestionRepository == null) {
            return null;
        }

        if (questionId != null && !questionId.isBlank()) {
            ExamQuestion question = examQuestionRepository.findByIdAndExam_Id(questionId.trim(), examId)
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found or does not belong to this exam: " + questionId));

            if ("DRAFT".equalsIgnoreCase(question.getStatus())) {
                throw new IllegalStateException("Question is not currently published for submissions: " + questionId);
            }
            return question;
        }

        // Single-question legacy fallback: resolve the unique/first question for this exam
        List<ExamQuestion> questions = examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId);
        if (questions.isEmpty()) {
            return null;
        }

        ExamQuestion defaultQuestion = questions.get(0);
        return examQuestionRepository.findByIdAndExam_IdForUpdate(defaultQuestion.getId(), examId).orElse(defaultQuestion);
    }

    @Transactional
    public SubmissionDetailResponse submitCode(String examId, String studentId, SubmissionCreateRequest request) {
        // Level 1 Lock: exams FOR SHARE
        Exam exam = examRepository.findByIdForShare(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));

        // Verify student enrollment
        examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        validateExamAvailability(exam);

        // Level 2 Lock: exam_questions FOR SHARE
        ExamQuestion question = resolveAndLockQuestion(examId, request.getQuestionId());

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        Submission submission = Submission.builder()
                .exam(exam)
                .question(question)
                .student(student)
                .sourceCode(request.getSourceCode())
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .build();

        submission = submissionRepository.save(submission);

        // Level 3 Lock & Atomic Sequence Upsert in SAME physical transaction
        if (studentQuestionScoreRepository != null && question != null) {
            String scoreId = UUID.randomUUID().toString();
            studentQuestionScoreRepository.upsertIntakeSequence(
                    scoreId,
                    exam.getId(),
                    question.getId(),
                    student.getId(),
                    submission.getId()
            );
        }

        // Dispatch grading job to BullMQ queue after transaction commits
        final String submissionId = submission.getId();
        final String examIdStr = exam.getId();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_COMMITTED) {
                                java.util.concurrent.CompletableFuture.runAsync(() -> {
                                    boolean enqueued = bullMqJobDispatcher != null && bullMqJobDispatcher.dispatchGradingJob(submissionId, examIdStr, "SINGLE_FILE");
                                    if (!enqueued) {
                                        if (internalGradingService != null) {
                                            try {
                                                internalGradingService.processGrading(submissionId);
                                            } catch (Exception e) {
                                                log.warn("Fallback direct grading error for submission {}: {}", submissionId, e.getMessage(), e);
                                            }
                                        } else if (automatedTestRunnerService != null) {
                                            try {
                                                Submission subToGrade = submissionRepository.findById(submissionId).orElse(null);
                                                if (subToGrade != null) {
                                                    automatedTestRunnerService.runAndGrade(subToGrade);
                                                }
                                            } catch (Exception e) {
                                                log.warn("Direct test runner error for submission {}: {}", submissionId, e.getMessage(), e);
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    }
            );
        } else {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                boolean enqueued = bullMqJobDispatcher != null && bullMqJobDispatcher.dispatchGradingJob(submissionId, examIdStr, "SINGLE_FILE");
                if (!enqueued) {
                    if (internalGradingService != null) {
                        try {
                            internalGradingService.processGrading(submissionId);
                        } catch (Exception e) {
                            log.warn("Fallback direct grading error for submission {}: {}", submissionId, e.getMessage(), e);
                        }
                    } else if (automatedTestRunnerService != null) {
                        try {
                            Submission subToGrade = submissionRepository.findById(submissionId).orElse(null);
                            if (subToGrade != null) {
                                automatedTestRunnerService.runAndGrade(subToGrade);
                            }
                        } catch (Exception e) {
                            log.warn("Direct test runner error for submission {}: {}", submissionId, e.getMessage(), e);
                        }
                    }
                }
            });
        }

        // Publish event for AST processing
        eventPublisher.publishEvent(new SubmissionCreatedEvent(this, exam.getId(), submission.getId()));

        return submissionMapper.toDetailResponse(submission);
    }

    public SubmissionDetailResponse submitZip(String examId, String studentId, MultipartFile file) {
        return submitZip(examId, null, studentId, file);
    }

    public SubmissionDetailResponse submitZip(String examId, String questionId, String studentId, MultipartFile file) {
        Exam exam = examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        validateExamAvailability(exam);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        ExamQuestion targetQuestion = resolveAndLockQuestion(examId, questionId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Submission file cannot be empty");
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("Submission file must have a valid filename");
        }

        // Normalize both Windows (\) and Unix (/) path separators before extracting the basename
        String normalizedPath = rawFilename.replace('\\', '/').trim();
        String originalFilename = Paths.get(normalizedPath).getFileName() != null
                ? Paths.get(normalizedPath).getFileName().toString()
                : "";

        if (originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Submission file must have a valid filename");
        }

        if (!originalFilename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP archive submissions (.zip) are accepted");
        }

        long maxBytes = zipProperties.getMaxCompressedBytes();
        if (file.getSize() > maxBytes) {
            throw new ZipValidationException(ZipValidationException.ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE,
                    "Archive size (" + file.getSize() + " bytes) exceeds maximum allowed size of " + maxBytes + " bytes");
        }

        byte[] zipBytes;
        try {
            zipBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read submission file: " + e.getMessage(), e);
        }

        if (zipBytes.length > maxBytes) {
            throw new ZipValidationException(ZipValidationException.ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE,
                    "Archive size exceeds maximum allowed size of " + maxBytes + " bytes");
        }

        String archiveHash = sourceHasherService.calculateArchiveSha256(zipBytes);

        try (ExtractedWorkspace workspace = zipExtractionService.extract(zipBytes)) {
            String sourceTreeHash;
            try {
                sourceTreeHash = sourceHasherService.calculateSourceTreeSha256(workspace.files());
            } catch (IOException e) {
                log.error("Failed to calculate source tree hash: {}", e.getMessage(), e);
                throw new IllegalStateException("Failed to calculate source tree hash: " + e.getMessage(), e);
            }
            int fileCount = workspace.files().size();

            // Discover entry point
            EntryPointResult entryPointResult = entryPointDiscoveryService.discoverEntryPoint(workspace);
            String entryPointClass = entryPointResult.entryPointClass();

            // TRANSACTION 1: Persist initial submission record AND atomic score sequence upsert together.
            // Level 1 (exams FOR SHARE) and Level 2 (exam_questions FOR SHARE) are acquired and held
            // INSIDE this dedicated transaction via the Spring proxy SubmissionPersistenceService.
            // If this transaction fails or rolls back, zero BullMQ jobs will be emitted.
            Submission initialSubmission = submissionPersistenceService.createInitialZipSubmission(
                    exam.getId(),
                    questionId,
                    exam,
                    targetQuestion,
                    student,
                    originalFilename,
                    archiveHash,
                    sourceTreeHash,
                    fileCount,
                    entryPointClass
            );

            // Persist ZIP file to storage (Guaranteed that DB record is safely committed first)
            if (submissionStorageService != null) {
                try {
                    String savedPath = submissionStorageService.saveZip(initialSubmission.getId(), zipBytes);
                    initialSubmission.setZipStoragePath(savedPath);
                    initialSubmission = submissionRepository.save(initialSubmission);
                } catch (Exception e) {
                    log.error("Failed to store submission archive for {}: {}", initialSubmission.getId(), e.getMessage());
                    initialSubmission.setStatus("FAILED");
                    initialSubmission.setExecutionStatus("STORAGE_FAILED");
                    initialSubmission.setCompileOutput("Submission archive storage failure: " + e.getMessage());

                    String targetQId = initialSubmission.getQuestion() != null ? initialSubmission.getQuestion().getId() : questionId;
                    if (submissionPersistenceService != null) {
                        submissionPersistenceService.failPendingSubmissionAndSyncScore(
                                initialSubmission.getId(),
                                null,
                                "STORAGE_FAILED",
                                "Submission archive storage failure: " + e.getMessage(),
                                targetQId,
                                student.getId()
                        );
                    } else {
                        submissionRepository.save(initialSubmission);
                        if (studentQuestionScoreRepository != null && targetQId != null && student != null) {
                            try {
                                studentQuestionScoreRepository.updateFailureStatusIfLatest(
                                        targetQId,
                                        student.getId(),
                                        initialSubmission.getId(),
                                        "FAILED"
                                );
                            } catch (Exception scoreEx) {
                                log.error("Failed to sync StudentQuestionScore failure status for {}: {}", initialSubmission.getId(), scoreEx.getMessage());
                            }
                        }
                    }

                    throw new IllegalStateException("Failed to store submission archive: " + e.getMessage(), e);
                }
            }

            Submission finalSubmission;
            // AUTO-GRADING: Run automated test runner against test cases
            if (automatedTestRunnerService != null) {
                try {
                    automatedTestRunnerService.runAndGrade(initialSubmission);
                } catch (Exception e) {
                    log.error("Automated test runner failure for submission {}: {}", initialSubmission.getId(), e.getMessage(), e);
                }
                finalSubmission = submissionRepository.findById(initialSubmission.getId()).orElse(initialSubmission);
            } else {
                // Fallback direct execution
                SandboxResult sandboxResult;
                try {
                    sandboxResult = dockerSandboxService.executeWorkspace(workspace, entryPointClass);
                } catch (Exception e) {
                    log.error("Sandbox execution failed for student {} in exam {}: {}", studentId, examId, e.getMessage(), e);
                    sandboxResult = SandboxResult.builder()
                            .status(SandboxResult.Status.INTERNAL_ERROR)
                            .stderr("Sandbox execution failure: " + e.getMessage())
                            .executionTimeMs(0L)
                            .build();
                }

                finalSubmission = submissionPersistenceService.updateExecutionTelemetryAndFingerprints(
                        initialSubmission.getId(), exam.getId(), sandboxResult, workspace
                );
            }

            // Refresh similarity matrix
            if (similarityMatrixService != null) {
                try {
                    similarityMatrixService.requestRefresh(exam.getId());
                    similarityMatrixService.generateMatrixForExam(exam.getId());
                } catch (Exception e) {
                    log.warn("Matrix refresh error for exam {}: {}", exam.getId(), e.getMessage());
                }
            }

            // Dispatch grading notification/job to BullMQ ONLY after initial persistence has successfully committed
            // and the ZIP file is stored on disk
            if (bullMqJobDispatcher != null) {
                boolean enqueued = bullMqJobDispatcher.dispatchGradingJob(finalSubmission.getId(), exam.getId(), "ZIP");
                if (enqueued) {
                    submissionRepository.recordSuccessfulInitialDispatch(finalSubmission.getId());
                }
            }

            return submissionMapper.toDetailResponse(finalSubmission);
        }
    }

    public List<SubmissionDetailResponse> submitBatchZip(
            String examId,
            String studentId,
            List<String> questionIds,
            List<MultipartFile> files) {

        Exam exam = examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        validateExamAvailability(exam);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        if (questionIds == null || files == null) {
            throw new IllegalArgumentException("Question IDs and files lists must not be null");
        }

        if (questionIds.size() != files.size()) {
            throw new IllegalArgumentException(String.format(
                    "Số lượng tệp ZIP (%d) không khớp với số lượng câu hỏi (%d)", files.size(), questionIds.size()));
        }

        if (questionIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách nộp bài không được để trống");
        }

        List<ExamQuestion> examQuestions = (examQuestionRepository != null)
                ? examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId)
                : java.util.Collections.emptyList();

        Map<String, ExamQuestion> questionMap = new java.util.HashMap<>();
        if (!examQuestions.isEmpty()) {
            for (ExamQuestion eq : examQuestions) {
                if (!"DRAFT".equalsIgnoreCase(eq.getStatus())) {
                    questionMap.put(eq.getId(), eq);
                }
            }
            if (questionMap.isEmpty()) {
                for (ExamQuestion eq : examQuestions) {
                    questionMap.put(eq.getId(), eq);
                }
            }

            java.util.Set<String> submittedQIds = new java.util.HashSet<>(questionIds);
            if (submittedQIds.size() != questionIds.size()) {
                throw new IllegalArgumentException("Phát hiện câu hỏi bị nộp trùng lặp trong cùng một lượt nộp.");
            }

            for (String requiredQId : questionMap.keySet()) {
                if (!submittedQIds.contains(requiredQId)) {
                    ExamQuestion missingQ = questionMap.get(requiredQId);
                    throw new IllegalArgumentException(String.format(
                            "Thiếu tệp ZIP bài làm cho câu hỏi: %s. Vui lòng nộp đầy đủ tất cả các câu hỏi.",
                            missingQ != null ? missingQ.getTitle() : requiredQId));
                }
            }

            for (String submittedQId : submittedQIds) {
                if (!questionMap.containsKey(submittedQId)) {
                    throw new IllegalArgumentException("Câu hỏi không tồn tại trong bài thi này: " + submittedQId);
                }
            }
        }

        // Atomic Pre-Validation
        class PreparedBatchItem {
            final ExamQuestion question;
            final byte[] zipBytes;
            final String originalFilename;
            final String archiveHash;
            final String sourceTreeHash;
            final int fileCount;
            final String entryPointClass;

            PreparedBatchItem(ExamQuestion question, byte[] zipBytes, String originalFilename,
                              String archiveHash, String sourceTreeHash, int fileCount, String entryPointClass) {
                this.question = question;
                this.zipBytes = zipBytes;
                this.originalFilename = originalFilename;
                this.archiveHash = archiveHash;
                this.sourceTreeHash = sourceTreeHash;
                this.fileCount = fileCount;
                this.entryPointClass = entryPointClass;
            }
        }

        List<PreparedBatchItem> preparedItems = new java.util.ArrayList<>();
        long maxBytes = zipProperties.getMaxCompressedBytes();

        // Pass 1: Validate file presence, filename and .zip extension across all items
        List<String> normalizedFilenames = new java.util.ArrayList<>();
        for (int i = 0; i < questionIds.size(); i++) {
            String qId = questionIds.get(i);
            MultipartFile file = files.get(i);
            ExamQuestion question = questionMap.get(qId);
            String qTitle = question != null ? question.getTitle() : ("Câu " + (i + 1));

            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException(String.format("Tệp nộp bài cho \"%s\" không được rỗng.", qTitle));
            }

            String rawFilename = file.getOriginalFilename();
            if (rawFilename == null || rawFilename.trim().isEmpty()) {
                throw new IllegalArgumentException(String.format("Tệp nộp bài cho \"%s\" phải có tên tệp hợp lệ.", qTitle));
            }

            String normalizedPath = rawFilename.replace('\\', '/').trim();
            String originalFilename = Paths.get(normalizedPath).getFileName() != null
                    ? Paths.get(normalizedPath).getFileName().toString()
                    : "";

            if (!originalFilename.toLowerCase().endsWith(".zip")) {
                throw new IllegalArgumentException(String.format(
                        "Tệp nộp bài cho \"%s\" không đúng định dạng. Chỉ chấp nhận tệp nén .zip.", qTitle));
            }

            if (file.getSize() > maxBytes) {
                throw new ZipValidationException(ZipValidationException.ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE,
                        String.format("Dung lượng tệp cho \"%s\" (%d bytes) vượt quá giới hạn tối đa %d bytes.", qTitle, file.getSize(), maxBytes));
            }
            normalizedFilenames.add(originalFilename);
        }

        // Pass 2: Extract and verify ZIP contents in memory
        for (int i = 0; i < questionIds.size(); i++) {
            String qId = questionIds.get(i);
            MultipartFile file = files.get(i);
            String originalFilename = normalizedFilenames.get(i);
            ExamQuestion question = questionMap.get(qId);
            String qTitle = question != null ? question.getTitle() : ("Câu " + (i + 1));

            byte[] zipBytes;
            try {
                zipBytes = file.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException(String.format("Không thể đọc tệp cho \"%s\": %s", qTitle, e.getMessage()), e);
            }

            if (zipBytes.length > maxBytes) {
                throw new ZipValidationException(ZipValidationException.ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE,
                        String.format("Dung lượng tệp cho \"%s\" vượt quá giới hạn tối đa %d bytes.", qTitle, maxBytes));
            }

            String archiveHash = sourceHasherService.calculateArchiveSha256(zipBytes);

            try (ExtractedWorkspace workspace = zipExtractionService != null ? zipExtractionService.extract(zipBytes) : null) {
                if (workspace == null) {
                    throw new ZipValidationException(ZipValidationException.ErrorCode.EXTRACTION_FAILED,
                            String.format("Không thể giải nén tệp ZIP cho \"%s\"", qTitle));
                }
                String sourceTreeHash;
                try {
                    sourceTreeHash = sourceHasherService.calculateSourceTreeSha256(workspace.files());
                } catch (IOException e) {
                    throw new IllegalStateException(String.format("Lỗi tính hash mã nguồn cho \"%s\": %s", qTitle, e.getMessage()), e);
                }
                int fileCount = workspace.files().size();
                EntryPointResult entryPointResult = entryPointDiscoveryService.discoverEntryPoint(workspace);
                String entryPointClass = entryPointResult.entryPointClass();

                preparedItems.add(new PreparedBatchItem(
                        question, zipBytes, originalFilename, archiveHash,
                        sourceTreeHash, fileCount, entryPointClass
                ));
            }
        }

        // All files validated safely without partial writes. Persist each submission.
        List<SubmissionDetailResponse> responses = new java.util.ArrayList<>();
        List<Submission> createdSubmissions = new java.util.ArrayList<>();
        List<String> savedZipSubmissionIds = new java.util.ArrayList<>();

        // Ensure deterministic question ID ordering for batch persistence to prevent deadlocks across concurrent submissions
        preparedItems.sort(Comparator.comparing(item -> item.question != null ? item.question.getId() : ""));

        try {
            for (PreparedBatchItem prep : preparedItems) {
                String targetQId = prep.question != null ? prep.question.getId() : null;

                Submission initialSubmission = submissionPersistenceService.createInitialZipSubmission(
                        exam.getId(),
                        targetQId,
                        exam,
                        prep.question,
                        student,
                        prep.originalFilename,
                        prep.archiveHash,
                        prep.sourceTreeHash,
                        prep.fileCount,
                        prep.entryPointClass
                );
                createdSubmissions.add(initialSubmission);

                if (submissionStorageService != null) {
                    try {
                        String savedPath = submissionStorageService.saveZip(initialSubmission.getId(), prep.zipBytes);
                        initialSubmission.setZipStoragePath(savedPath);
                        initialSubmission = submissionRepository.save(initialSubmission);
                        savedZipSubmissionIds.add(initialSubmission.getId());
                    } catch (Exception e) {
                        log.error("Failed to store submission archive for {}: {}", initialSubmission.getId(), e.getMessage());
                        throw new IllegalStateException("Failed to store submission archive: " + e.getMessage(), e);
                    }
                }

                Submission finalSubmission;
                if (automatedTestRunnerService != null) {
                    try {
                        automatedTestRunnerService.runAndGrade(initialSubmission);
                    } catch (Exception e) {
                        log.error("Automated test runner failure for submission {}: {}", initialSubmission.getId(), e.getMessage(), e);
                    }
                    finalSubmission = submissionRepository.findById(initialSubmission.getId()).orElse(initialSubmission);
                } else {
                    finalSubmission = initialSubmission;
                }

                eventPublisher.publishEvent(new SubmissionCreatedEvent(this, exam.getId(), finalSubmission.getId()));
                responses.add(submissionMapper.toDetailResponse(finalSubmission));
            }
        } catch (Exception ex) {
            log.error("Batch submission failed for exam {}. Rolling back persisted submissions and files: {}", examId, ex.getMessage());
            if (submissionStorageService != null) {
                for (String subId : savedZipSubmissionIds) {
                    try {
                        submissionStorageService.deleteZip(subId);
                    } catch (Exception e) {
                        log.warn("Failed to delete orphaned submission ZIP for {}: {}", subId, e.getMessage());
                    }
                }
            }
            for (Submission created : createdSubmissions) {
                try {
                    submissionRepository.delete(created);
                } catch (Exception e) {
                    log.warn("Failed to delete orphaned submission record for {}: {}", created.getId(), e.getMessage());
                }
            }
            throw ex;
        }

        if (similarityMatrixService != null) {
            try {
                similarityMatrixService.requestRefresh(exam.getId());
                similarityMatrixService.generateMatrixForExam(exam.getId());
            } catch (Exception e) {
                log.warn("Matrix refresh error after batch submission for exam {}: {}", exam.getId(), e.getMessage());
            }
        }

        return responses;
    }
}
