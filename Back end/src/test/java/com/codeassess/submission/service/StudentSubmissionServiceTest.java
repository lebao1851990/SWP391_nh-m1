package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import com.codeassess.submission.exception.ZipValidationException;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StudentSubmissionServiceTest {

    private SubmissionRepository submissionRepository;
    private SubmissionMapper submissionMapper;
    private ExamRepository examRepository;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private ZipExtractionService zipExtractionService;
    private SourceHasherService sourceHasherService;
    private EntryPointDiscoveryService entryPointDiscoveryService;
    private DockerSandboxService dockerSandboxService;
    private AstPlagiarismService astPlagiarismService;
    private ZipSubmissionProperties zipProperties;
    private SubmissionPersistenceService submissionPersistenceService;

    private StudentSubmissionService service;

    private Exam testExam;
    private User testStudent;

    @BeforeEach
    void setUp() {
        submissionRepository = mock(SubmissionRepository.class);
        submissionMapper = new SubmissionMapper();
        examRepository = mock(ExamRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        zipExtractionService = mock(ZipExtractionService.class);
        sourceHasherService = mock(SourceHasherService.class);
        entryPointDiscoveryService = mock(EntryPointDiscoveryService.class);
        dockerSandboxService = mock(DockerSandboxService.class);
        astPlagiarismService = mock(AstPlagiarismService.class);
        zipProperties = new ZipSubmissionProperties();
        submissionPersistenceService = spy(new SubmissionPersistenceService(submissionRepository, astPlagiarismService, eventPublisher));

        service = new StudentSubmissionService(
                submissionRepository,
                submissionMapper,
                examRepository,
                userRepository,
                eventPublisher,
                zipExtractionService,
                sourceHasherService,
                entryPointDiscoveryService,
                dockerSandboxService,
                astPlagiarismService,
                zipProperties,
                submissionPersistenceService
        );

        testExam = Exam.builder()
                .id("exam-100")
                .title("Midterm Exam")
                .status("ACTIVE")
                .build();

        testStudent = User.builder()
                .id("student-100")
                .email("student@test.com")
                .fullName("John Doe")
                .role(Role.STUDENT)
                .build();

        Map<String, Submission> mockDb = new java.util.concurrent.ConcurrentHashMap<>();
        when(examRepository.findByIdAndStudentId("exam-100", "student-100")).thenReturn(Optional.of(testExam));
        when(userRepository.findById("student-100")).thenReturn(Optional.of(testStudent));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission sub = invocation.getArgument(0);
            if (sub.getId() == null) {
                sub.setId("sub-generated-id");
            }
            mockDb.put(sub.getId(), sub);
            return sub;
        });
        when(submissionRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.ofNullable(mockDb.get(id));
        });
    }

    private ExtractedWorkspace createMockWorkspace(Path tempDir) throws IOException {
        Path f1 = tempDir.resolve("Main.java");
        Files.writeString(f1, "public class Main { public static void main(String[] args) {} }");
        Path f2 = tempDir.resolve("Helper.java");
        Files.writeString(f2, "public class Helper {}");

        List<ExtractedFile> files = List.of(
                new ExtractedFile("Main.java", f1, Files.size(f1), true),
                new ExtractedFile("Helper.java", f2, Files.size(f2), true)
        );
        return new ExtractedWorkspace(tempDir, files, 2048L, "archive-sha256-hex");
    }

    @Test
    @DisplayName("submitZip: Valid ZIP processes hashes, entry point, sandbox execution, AST, and publishes event")
    void submitZip_validZip_success() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "dummy-zip-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "solution.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("archive-sha256-hex");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-sha256-hex");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace))
                .thenReturn(EntryPointResult.found("Main", List.of("Main")));

        SandboxResult sandboxResult = SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .exitCode(0)
                .stdout("Hello World\n")
                .stderr("")
                .executionTimeMs(120L)
                .build();
        when(dockerSandboxService.executeWorkspace(workspace, "Main")).thenReturn(sandboxResult);

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("sub-generated-id");
        assertThat(response.getSubmissionType()).isEqualTo("ZIP");
        assertThat(response.getOriginalFilename()).isEqualTo("solution.zip");
        assertThat(response.getArchiveHash()).isEqualTo("archive-sha256-hex");
        assertThat(response.getSourceTreeHash()).isEqualTo("tree-sha256-hex");
        assertThat(response.getFileCount()).isEqualTo(2);
        assertThat(response.getEntryPointClass()).isEqualTo("Main");
        assertThat(response.getExecutionStatus()).isEqualTo("SUCCESS");
        assertThat(response.getExitCode()).isEqualTo(0);
        assertThat(response.getStdout()).isEqualTo("Hello World\n");
        assertThat(response.getExecutionDurationMs()).isEqualTo(120L);

        // Verify AST fingerprinting was invoked with workspace
        verify(astPlagiarismService).generateFingerprintsForWorkspace(any(Submission.class), eq(workspace));

        // Verify event was published
        verify(eventPublisher).publishEvent(any(SubmissionCreatedEvent.class));

        // Verify workspace was closed
        verify(workspace).close();
        assertThat(Files.exists(tempDir)).isFalse();
    }

    @Test
    @DisplayName("submitZip: Empty file is rejected with 400 IllegalArgumentException")
    void submitZip_emptyFile_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]);

        assertThatThrownBy(() -> service.submitZip("exam-100", "student-100", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Submission file cannot be empty");

        verifyNoInteractions(zipExtractionService);
        verifyNoInteractions(submissionRepository);
    }

    @Test
    @DisplayName("submitZip: Non-ZIP extension is rejected with IllegalArgumentException")
    void submitZip_nonZipExtension_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "malicious.tar.gz", "application/gzip", "content".getBytes());

        assertThatThrownBy(() -> service.submitZip("exam-100", "student-100", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only ZIP archive submissions (.zip) are accepted");

        verifyNoInteractions(zipExtractionService);
    }

    @Test
    @DisplayName("submitZip: Path traversal in originalFilename is sanitized")
    void submitZip_pathTraversalFilename_isSanitized() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-traversal-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "dummy-zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd/hack.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace))
                .thenReturn(EntryPointResult.none());
        when(dockerSandboxService.executeWorkspace(workspace, null))
                .thenReturn(SandboxResult.builder().status(SandboxResult.Status.COMPILE_ONLY_SUCCESS).exitCode(0).build());

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);

        // Path traversal stripped: only the basename "hack.zip" is stored
        assertThat(response.getOriginalFilename()).isEqualTo("hack.zip");
        verify(workspace).close();
    }

    @Test
    @DisplayName("submitZip: Windows-style backslash path is normalized to basename")
    void submitZip_windowsPath_basenameExtracted() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-win-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "dummy-zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "C:\\Users\\Student\\workspace\\my_solution.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace)).thenReturn(EntryPointResult.none());
        when(dockerSandboxService.executeWorkspace(workspace, null))
                .thenReturn(SandboxResult.builder().status(SandboxResult.Status.COMPILE_ONLY_SUCCESS).exitCode(0).build());

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);
        assertThat(response.getOriginalFilename()).isEqualTo("my_solution.zip");
        verify(workspace).close();
    }

    @Test
    @DisplayName("submitZip: Unix-style forward-slash path is normalized to basename")
    void submitZip_unixPath_basenameExtracted() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-unix-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "dummy-zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "/var/tmp/upload/exam_assignment.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace)).thenReturn(EntryPointResult.none());
        when(dockerSandboxService.executeWorkspace(workspace, null))
                .thenReturn(SandboxResult.builder().status(SandboxResult.Status.COMPILE_ONLY_SUCCESS).exitCode(0).build());

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);
        assertThat(response.getOriginalFilename()).isEqualTo("exam_assignment.zip");
        verify(workspace).close();
    }

    @Test
    @DisplayName("submitZip: Blank or whitespace filename throws IllegalArgumentException")
    void submitZip_blankFilename_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "   ", "application/zip", "dummy-zip".getBytes());

        assertThatThrownBy(() -> service.submitZip("exam-100", "student-100", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Submission file must have a valid filename");

        verifyNoInteractions(zipExtractionService);
    }

    @Test
    @DisplayName("submitZip: Oversized archive upload is rejected before decompression")
    void submitZip_oversizedArchive_throwsZipValidationException() {
        zipProperties.setMaxCompressedBytes(1024); // 1 KiB ceiling for test
        byte[] oversizedBytes = new byte[2048];
        MockMultipartFile file = new MockMultipartFile("file", "oversized.zip", "application/zip", oversizedBytes);

        assertThatThrownBy(() -> service.submitZip("exam-100", "student-100", file))
                .isInstanceOf(ZipValidationException.class)
                .hasMessageContaining("exceeds maximum allowed size");

        verifyNoInteractions(zipExtractionService);
    }

    @Test
    @DisplayName("submitZip: Inactive exam throws IllegalStateException")
    void submitZip_inactiveExam_throwsIllegalState() {
        testExam.setStatus("CLOSED");
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "bytes".getBytes());

        assertThatThrownBy(() -> service.submitZip("exam-100", "student-100", file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exam is not currently active");

        verifyNoInteractions(zipExtractionService);
    }

    @Test
    @DisplayName("submitZip: Student not enrolled in class throws ResourceNotFoundException")
    void submitZip_notEnrolled_throwsResourceNotFound() {
        when(examRepository.findByIdAndStudentId("exam-100", "unenrolled-student")).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "bytes".getBytes());

        assertThatThrownBy(() -> service.submitZip("exam-100", "unenrolled-student", file))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Exam not found or you are not enrolled");

        verifyNoInteractions(zipExtractionService);
    }

    @Test
    @DisplayName("submitZip: Compilation error records COMPILE_ERROR telemetry and still runs AST fingerprinting")
    void submitZip_compileError_recordsTelemetryAndFingerprints() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-compile-err-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "compile_fail.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace))
                .thenReturn(EntryPointResult.found("Main", List.of("Main")));

        SandboxResult compileErrorResult = SandboxResult.builder()
                .status(SandboxResult.Status.COMPILE_ERROR)
                .exitCode(1)
                .compilerOutput("Main.java:2: error: ';' expected\n")
                .executionTimeMs(85L)
                .build();
        when(dockerSandboxService.executeWorkspace(workspace, "Main")).thenReturn(compileErrorResult);

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);

        assertThat(response.getExecutionStatus()).isEqualTo("COMPILE_ERROR");
        assertThat(response.getExitCode()).isEqualTo(1);
        assertThat(response.getCompileOutput()).contains("error: ';' expected");

        // AST fingerprinting is still executed for syntactically valid files
        verify(astPlagiarismService).generateFingerprintsForWorkspace(any(Submission.class), eq(workspace));
        verify(workspace).close();
    }

    @Test
    @DisplayName("submitZip: 90 KiB inline TAR capacity exceeded records CAPACITY_EXCEEDED without losing submission")
    void submitZip_capacityExceeded_recordsControlledCapacityError() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-cap-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "large_project.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace))
                .thenReturn(EntryPointResult.found("Main", List.of("Main")));

        SandboxResult capacityResult = SandboxResult.builder()
                .status(SandboxResult.Status.CAPACITY_EXCEEDED)
                .stderr("Workspace archive size (95000 bytes) exceeds the inline container transfer limit of 92160 bytes")
                .executionTimeMs(0L)
                .build();
        when(dockerSandboxService.executeWorkspace(workspace, "Main")).thenReturn(capacityResult);

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);

        assertThat(response.getExecutionStatus()).isEqualTo("CAPACITY_EXCEEDED");
        assertThat(response.getStderr()).contains("exceeds the inline container transfer limit of 92160 bytes");
        assertThat(response.getArchiveHash()).isEqualTo("arch-hash");
        verify(workspace).close();
    }

    @Test
    @DisplayName("submitZip: Execution timeout records TIMEOUT status and cleans up workspace")
    void submitZip_timeout_recordsTimeoutStatus() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-timeout-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "infinite_loop.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace))
                .thenReturn(EntryPointResult.found("Main", List.of("Main")));

        SandboxResult timeoutResult = SandboxResult.builder()
                .status(SandboxResult.Status.TIMEOUT)
                .stderr("Execution timed out after 10 seconds")
                .executionTimeMs(10005L)
                .build();
        when(dockerSandboxService.executeWorkspace(workspace, "Main")).thenReturn(timeoutResult);

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);

        assertThat(response.getExecutionStatus()).isEqualTo("TIMEOUT");
        assertThat(response.getStderr()).contains("Execution timed out");
        verify(workspace).close();
    }

    @Test
    @DisplayName("submitZip: Sandbox system exception is captured gracefully as INTERNAL_ERROR without losing submission")
    void submitZip_sandboxException_recordedAsInternalError() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-syserr-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "sys_err.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace))
                .thenReturn(EntryPointResult.found("Main", List.of("Main")));

        when(dockerSandboxService.executeWorkspace(workspace, "Main"))
                .thenThrow(new RuntimeException("Docker daemon socket closed unexpectedly"));

        SubmissionDetailResponse response = service.submitZip("exam-100", "student-100", file);

        assertThat(response.getExecutionStatus()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getStderr()).contains("Docker daemon socket closed unexpectedly");
        verify(workspace).close();
    }

    @Test
    @DisplayName("submitZip: Workspace is unconditionally closed even when submissionRepository throws")
    void submitZip_workspaceClosedOnDbFailure() throws Exception {
        Path tempDir = Files.createTempDirectory("test-ws-dbfail-");
        ExtractedWorkspace workspace = spy(createMockWorkspace(tempDir));

        byte[] zipBytes = "zip".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "db_fail.zip", "application/zip", zipBytes);

        when(sourceHasherService.calculateArchiveSha256(zipBytes)).thenReturn("arch-hash");
        when(zipExtractionService.extract(zipBytes)).thenReturn(workspace);
        when(sourceHasherService.calculateSourceTreeSha256(workspace.files())).thenReturn("tree-hash");
        when(entryPointDiscoveryService.discoverEntryPoint(workspace))
                .thenReturn(EntryPointResult.found("Main", List.of("Main")));
        when(dockerSandboxService.executeWorkspace(workspace, "Main"))
                .thenReturn(SandboxResult.builder().status(SandboxResult.Status.SUCCESS).exitCode(0).build());

        when(submissionRepository.save(any(Submission.class))).thenThrow(new RuntimeException("Database constraint violation"));

        assertThatThrownBy(() -> service.submitZip("exam-100", "student-100", file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database constraint violation");

        // CRITICAL INVARIANT: workspace MUST be closed despite DB exception
        verify(workspace).close();
        assertThat(Files.exists(tempDir)).isFalse();
    }

    @Test
    @DisplayName("submitCode: Backward compatibility for single-file JSON submission is preserved")
    void submitCode_singleFileJsonCompatibility() {
        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Main {}");

        SubmissionDetailResponse response = service.submitCode("exam-100", "student-100", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("sub-generated-id");
        assertThat(response.getSubmissionType()).isEqualTo("SINGLE_FILE");
        assertThat(response.getSourceCode()).isEqualTo("public class Main {}");
        assertThat(response.getStatus()).isEqualTo("PENDING");

        verify(eventPublisher).publishEvent(any(SubmissionCreatedEvent.class));
        verifyNoInteractions(zipExtractionService);
        verifyNoInteractions(dockerSandboxService);
    }

    @Test
    @DisplayName("Multi-file AST Evidence: EvidenceGenerator records exact file paths and line ranges")
    void multiFileAstEvidence_recordsExactFileLocations() {
        EvidenceGenerator generator = new EvidenceGenerator();
        SimilarityPair pair = SimilarityPair.builder().id(1L).build();

        AstFingerprint fpA = AstFingerprint.builder()
                .hashValue(123456789L)
                .nodeType("MethodDeclaration")
                .startLine(12)
                .endLine(28)
                .filePath("src/com/example/OrderService.java")
                .build();

        AstFingerprint fpB = AstFingerprint.builder()
                .hashValue(123456789L)
                .nodeType("MethodDeclaration")
                .startLine(15)
                .endLine(31)
                .filePath("com/billing/PaymentService.java")
                .build();

        List<PlagiarismEvidence> evidences = generator.generateEvidence(pair, List.of(fpA), List.of(fpB));

        assertThat(evidences).hasSize(1);
        PlagiarismEvidence evidence = evidences.get(0);
        assertThat(evidence.getEvidenceType()).isEqualTo("MethodDeclaration");
        assertThat(evidence.getLocationA()).isEqualTo("src/com/example/OrderService.java:12-28");
        assertThat(evidence.getLocationB()).isEqualTo("com/billing/PaymentService.java:15-31");
        assertThat(evidence.getDescription()).contains("src/com/example/OrderService.java:12-28");
    }
}
