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
import java.time.LocalDateTime;
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
    private final InternalGradingService internalGradingService;
    private final com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService;
    private final com.codeassess.queue.service.BullMqJobDispatcher bullMqJobDispatcher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SubmissionStorageService submissionStorageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
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
                .map(submissionMapper::toListResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getStudentSubmissionById(String id, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(id, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));
        return submissionMapper.toDetailResponse(submission);
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

        if (exam.getStatus() == null || !exam.getStatus().equalsIgnoreCase("active")) {
            throw new IllegalStateException("Exam is not currently active for submissions");
        }

        if (exam.getExamDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(exam.getExamDate())) {
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

    @Transactional
    public SubmissionDetailResponse submitCode(String examId, String studentId, SubmissionCreateRequest request) {
        Exam exam = examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        validateExamAvailability(exam);

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

        // Dispatch grading job to BullMQ queue after transaction commits, with immediate fallback if queue offline
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
        Exam exam = examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        validateExamAvailability(exam);

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

            // Persist ZIP file to storage
            if (submissionStorageService != null) {
                try {
                    String savedPath = submissionStorageService.saveZip(initialSubmission.getId(), zipBytes);
                    initialSubmission.setZipStoragePath(savedPath);
                    initialSubmission = submissionRepository.save(initialSubmission);
                } catch (Exception e) {
                    log.error("Failed to store submission archive for {}: {}", initialSubmission.getId(), e.getMessage());
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

            // Dispatch grading completion notification/job to BullMQ
            if (bullMqJobDispatcher != null) {
                bullMqJobDispatcher.dispatchGradingJob(finalSubmission.getId(), exam.getId(), "ZIP");
            }

            return submissionMapper.toDetailResponse(finalSubmission);
        }
    }
}
