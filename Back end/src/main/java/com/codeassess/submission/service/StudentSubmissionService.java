package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
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
import java.util.List;
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

    @Autowired(required = false)
    private com.codeassess.queue.service.BullMqJobDispatcher bullMqJobDispatcher;

    public StudentSubmissionService(
            SubmissionRepository submissionRepository,
            SubmissionMapper submissionMapper,
            ExamRepository examRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this(submissionRepository, submissionMapper, examRepository, userRepository, eventPublisher,
                null, null, null, null, null, new ZipSubmissionProperties(), null);
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
            SubmissionPersistenceService submissionPersistenceService) {
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
    }

    @Transactional(readOnly = true)
    public List<SubmissionListResponse> getStudentSubmissions(String studentId) {
        return submissionRepository.findByStudent_Id(studentId).stream()
                .map(submissionMapper::toListResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getStudentSubmissionById(String id, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(id, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));
        return submissionMapper.toDetailResponse(submission);
    }

    @Transactional
    public SubmissionDetailResponse submitCode(String examId, String studentId, SubmissionCreateRequest request) {
        Exam exam = examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        if (exam.getStatus() == null || !exam.getStatus().equalsIgnoreCase("active")) {
            throw new IllegalStateException("Exam is not currently active for submissions");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        Submission submission = Submission.builder()
                .exam(exam)
                .student(student)
                .sourceCode(request.getSourceCode())
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .build();

        submission = submissionRepository.save(submission);

        // Dispatch grading job to BullMQ queue after transaction commits
        final String submissionId = submission.getId();
        final String examIdStr = exam.getId();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            if (bullMqJobDispatcher != null) {
                                bullMqJobDispatcher.dispatchGradingJob(submissionId, examIdStr, "SINGLE_FILE");
                            }
                        }
                    }
            );
        } else if (bullMqJobDispatcher != null) {
            bullMqJobDispatcher.dispatchGradingJob(submissionId, examIdStr, "SINGLE_FILE");
        }

        // Publish event for AST processing
        eventPublisher.publishEvent(new SubmissionCreatedEvent(this, exam.getId(), submission.getId()));

        return submissionMapper.toDetailResponse(submission);
    }

    public SubmissionDetailResponse submitZip(String examId, String studentId, MultipartFile file) {
        Exam exam = examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        if (exam.getStatus() == null || !exam.getStatus().equalsIgnoreCase("active")) {
            throw new IllegalStateException("Exam is not currently active for submissions");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

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

            // TRANSACTION 1: Persist initial submission record (committed before Docker runs)
            Submission initialSubmission = submissionPersistenceService.createInitialZipSubmission(
                    exam, student, originalFilename, archiveHash, sourceTreeHash, fileCount, entryPointClass
            );

            // DOCKER EXECUTION: Executed completely outside any database transaction (no JDBC connection held)
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

            // TRANSACTION 2: Persist telemetry and AST fingerprints
            Submission finalSubmission = submissionPersistenceService.updateExecutionTelemetryAndFingerprints(
                    initialSubmission.getId(), exam.getId(), sandboxResult, workspace
            );

            // Dispatch grading completion notification/job to BullMQ
            if (bullMqJobDispatcher != null) {
                bullMqJobDispatcher.dispatchGradingJob(finalSubmission.getId(), exam.getId(), "ZIP");
            }

            return submissionMapper.toDetailResponse(finalSubmission);
        }
    }
}
