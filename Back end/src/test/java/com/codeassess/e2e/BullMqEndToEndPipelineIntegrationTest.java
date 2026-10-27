package com.codeassess.e2e;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.ExamMatrixCoordinationRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.SubmissionRecoverySweeper;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full End-to-End integration test for the BullMQ Hybrid Architecture.
 * Exercises the complete real chain with NO MOCKS:
 * - Real MySQL database (plagiarism_system_test)
 * - Real Redis instance (localhost:6379)
 * - Real BullMQ Node.js Producer and Worker process
 * - Real Spring Boot Internal API with X-Internal-Token authentication
 * - Real Docker Sandbox container execution (eclipse-temurin:21-jdk-alpine)
 * - Real AST Parser and Winnowing Fingerprint persistence
 * - Real Matrix Coordination updates
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=8089",
        "queue.bullmq.producer-url=http://localhost:3001"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BullMqEndToEndPipelineIntegrationTest {

    private static final String BULLMQ_SERVICE_DIR = new File("../bullmq-service").getAbsolutePath();
    private static final String NODE_BINARY = "node";
    private static Process nodeWorkerProcess;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseClassRepository courseClassRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AstFingerprintRepository astFingerprintRepository;

    @Autowired
    private SimilarityPairRepository similarityPairRepository;

    @Autowired
    private PlagiarismEvidenceRepository plagiarismEvidenceRepository;

    @Autowired
    private AppealRepository appealRepository;

    @Autowired
    private ExamMatrixCoordinationRepository examMatrixCoordinationRepository;

    @Autowired
    private SubmissionRecoverySweeper submissionRecoverySweeper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    private User teacher;
    private User student1;
    private CourseClass courseClass;
    private Exam exam;

    @BeforeAll
    static void startBullMqService() throws Exception {
        // Start live BullMQ Node.js process pointing to Spring Boot at port 8089
        ProcessBuilder pb = new ProcessBuilder(NODE_BINARY, "src/server.js");
        pb.directory(new File(BULLMQ_SERVICE_DIR));
        Map<String, String> env = pb.environment();
        env.put("PORT", "3001");
        env.put("BACKEND_URL", "http://localhost:8089");
        env.put("REDIS_HOST", "localhost");
        env.put("REDIS_PORT", "6379");
        env.put("INTERNAL_API_SECRET", "codeassess-internal-secret-2026");
        env.put("BULLMQ_CONCURRENCY", "2");

        nodeWorkerProcess = pb.start();

        // Wait up to 10s for BullMQ service to be UP
        TestRestTemplate probe = new TestRestTemplate();
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
            try {
                ResponseEntity<Map> res = probe.getForEntity("http://localhost:3001/api/health", Map.class);
                return res.getStatusCode() == HttpStatus.OK && "UP".equals(res.getBody().get("status"));
            } catch (Exception e) {
                return false;
            }
        });
    }

    @AfterAll
    static void stopBullMqService() {
        if (nodeWorkerProcess != null && nodeWorkerProcess.isAlive()) {
            nodeWorkerProcess.destroyForcibly();
        }
    }

    @BeforeEach
    void setUpTestData() {
        com.codeassess.testconfig.DatabaseSafetyGuard.assertSafeTestDatabase(dataSource);

        appealRepository.deleteAll();
        plagiarismEvidenceRepository.deleteAll();
        similarityPairRepository.deleteAll();
        astFingerprintRepository.deleteAll();
        submissionRepository.deleteAll();
        examMatrixCoordinationRepository.deleteAll();
        examRepository.deleteAll();
        classStudentRepository.deleteAll();
        courseClassRepository.deleteAll();
        userRepository.deleteAll();

        String pass = passwordEncoder.encode("testpass123");
        teacher = userRepository.save(User.builder()
                .email("teacher-bullmq@fpt.edu.vn")
                .fullName("Teacher BullMQ")
                .role(Role.TEACHER)
                .passwordHash(pass)
                .build());

        student1 = userRepository.save(User.builder()
                .email("student1-bullmq@fpt.edu.vn")
                .fullName("Student1 BullMQ")
                .role(Role.STUDENT)
                .passwordHash(pass)
                .build());

        courseClass = courseClassRepository.save(CourseClass.builder()
                .name("BullMQ Integration Class")
                .code("BMQ101")
                .teacher(teacher)
                .build());

        classStudentRepository.save(ClassStudent.builder()
                .id(new ClassStudent.ClassStudentId(courseClass.getId(), student1.getId()))
                .courseClass(courseClass)
                .student(student1)
                .build());

        exam = examRepository.save(Exam.builder()
                .title("BullMQ Practical Exam")
                .courseClass(courseClass)
                .examDate(LocalDateTime.now().plusDays(1))
                .duration(90)
                .status("ACTIVE")
                .build());
    }

    private HttpHeaders getStudentAuthHeaders() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("student1-bullmq@fpt.edu.vn");
        loginRequest.setPassword("testpass123");

        ResponseEntity<Void> loginResponse = restTemplate.postForEntity(
                "http://localhost:8089/api/auth/login", loginRequest, Void.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> cookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders headers = new HttpHeaders();
        if (cookies != null) {
            headers.put(HttpHeaders.COOKIE, cookies);
        }
        return headers;
    }

    // =========================================================================
    // SECTION 3: End-to-End Test for Single-File Submission (11 Steps Evidence)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("E2E Single-File: Verify complete BullMQ 11-step pipeline from student submission to completed Docker execution")
    void testSingleFileSubmission_EndToEndPipeline() {
        HttpHeaders headers = getStudentAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String javaSource = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"E2E BullMQ Execution Success\");\n" +
                "    }\n" +
                "}";

        SubmissionCreateRequest request = new SubmissionCreateRequest(javaSource);

        // 1 & 2. Student submits code via real endpoint
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:8089/api/student/exams/" + exam.getId() + "/submissions",
                new HttpEntity<>(request, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String submissionId = (String) response.getBody().get("id");
        assertThat(submissionId).isNotNull();

        // 3. Verify initial state saved in MySQL
        Submission initialSub = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(initialSub.getStatus()).isIn("PENDING", "COMPLETED"); // May transition fast
        assertThat(initialSub.getSubmissionType()).isEqualTo("SINGLE_FILE");

        // 4, 5, 6, 7, 8, 9, 10, 11: Wait for BullMQ worker to process via real Docker
        // Sandbox
        await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            Submission sub = submissionRepository.findById(submissionId).orElse(null);
            return sub != null && "COMPLETED".equals(sub.getStatus());
        });

        // Verify evidence across all pipeline components
        Submission finalSub = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(finalSub.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalSub.getExecutionStatus()).isEqualTo("SUCCESS");
        assertThat(finalSub.getExitCode()).isEqualTo(0);
        assertThat(finalSub.getStdout()).contains("E2E BullMQ Execution Success");
        assertThat(finalSub.getExecutionDurationMs()).isGreaterThan(0L);
        assertThat(finalSub.getEvaluatedAt()).isNotNull();

        // Verify AST fingerprints were generated and saved in DB
        int fingerprintCount = astFingerprintRepository.findBySubmission_Id(submissionId).size();
        assertThat(fingerprintCount).isGreaterThan(0);

        // Verify BullMQ job status via BullMQ HTTP API
        ResponseEntity<Map> jobStatusRes = restTemplate.getForEntity(
                "http://localhost:3001/api/jobs/grading-" + submissionId, Map.class);
        assertThat(jobStatusRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jobStatusRes.getBody().get("state")).isIn("completed", "active");
    }

    // =========================================================================
    // SECTION 3: End-to-End Test for ZIP Submission
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("E2E ZIP: Verify complete BullMQ pipeline for multi-file ZIP archive submission")
    void testZipSubmission_EndToEndPipeline() throws IOException {
        HttpHeaders headers = getStudentAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create in-memory ZIP archive
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("src/Calculator.java"));
            String calcCode = "package src;\npublic class Calculator {\n" +
                    "    public int add(int a, int b) { return a + b; }\n" +
                    "}";
            zos.write(calcCode.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("src/App.java"));
            String appCode = "package src;\npublic class App {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        Calculator c = new Calculator();\n" +
                    "        System.out.println(\"Calc result: \" + c.add(10, 20));\n" +
                    "    }\n" +
                    "}";
            zos.write(appCode.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        ByteArrayResource resource = new ByteArrayResource(zipBytes) {
            @Override
            public String getFilename() {
                return "submission.zip";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:8089/api/student/exams/" + exam.getId() + "/submissions",
                new HttpEntity<>(body, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String submissionId = (String) response.getBody().get("id");
        assertThat(submissionId).isNotNull();

        // Wait for BullMQ worker completion
        await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            Submission sub = submissionRepository.findById(submissionId).orElse(null);
            return sub != null && "COMPLETED".equals(sub.getStatus());
        });

        Submission finalSub = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(finalSub.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalSub.getSubmissionType()).isEqualTo("ZIP");
        assertThat(finalSub.getArchiveHash()).isNotNull();
        assertThat(finalSub.getSourceTreeHash()).isNotNull();
        assertThat(finalSub.getExecutionStatus()).isEqualTo("SUCCESS");
        assertThat(finalSub.getExitCode()).isEqualTo(0);
        assertThat(finalSub.getStdout()).contains("Calc result: 30");

        // Verify AST fingerprints saved for multi-file workspace
        int fingerprintCount = astFingerprintRepository.findBySubmission_Id(submissionId).size();
        assertThat(fingerprintCount).isGreaterThan(0);
    }

    // =========================================================================
    // SECTION 4: Failure & Recovery Tests
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Failure Case: Internal API rejects unauthorized calls with missing/invalid token (401)")
    void testInternalApi_SecurityRejection() throws Exception {
        Submission sub = submissionRepository.save(Submission.builder()
                .exam(exam)
                .student(student1)
                .status("PENDING")
                .sourceCode("public class Test {}")
                .submissionType("SINGLE_FILE")
                .build());

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

        // 1. Missing header
        java.net.http.HttpRequest reqMissing = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI
                        .create("http://localhost:8089/api/internal/submissions/" + sub.getId() + "/process-grading"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        java.net.http.HttpResponse<String> respMissing = client.send(reqMissing,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(respMissing.statusCode()).isEqualTo(401);

        // 2. Invalid header
        java.net.http.HttpRequest reqBad = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI
                        .create("http://localhost:8089/api/internal/submissions/" + sub.getId() + "/process-grading"))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", "wrong-invalid-secret-token")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        java.net.http.HttpResponse<String> respBad = client.send(reqBad,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(respBad.statusCode()).isEqualTo(401);

        // Verify submission remained untouched in PENDING state
        Submission unchanged = submissionRepository.findById(sub.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @Order(4)
    @DisplayName("Failure Case: Compilation error in Docker sandbox is captured without crashing pipeline")
    void testCompilationFailure_CapturedGracefully() {
        HttpHeaders headers = getStudentAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Invalid Java syntax in class Main
        String brokenJava = "public class Main {\n    public static void main(String[] args) {\n        int error = ;\n    }\n}";
        SubmissionCreateRequest request = new SubmissionCreateRequest(brokenJava);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:8089/api/student/exams/" + exam.getId() + "/submissions",
                new HttpEntity<>(request, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String submissionId = (String) response.getBody().get("id");

        await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            Submission sub = submissionRepository.findById(submissionId).orElse(null);
            return sub != null && "COMPLETED".equals(sub.getStatus());
        });

        Submission finalSub = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(finalSub.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalSub.getExecutionStatus()).isEqualTo("COMPILE_ERROR");
        assertThat(finalSub.getExitCode()).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("Failure Case: Corrupted ZIP submission is rejected before execution (400 Bad Request)")
    void testCorruptZipSubmission_RejectedImmediately() {
        HttpHeaders headers = getStudentAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        byte[] corruptBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        ByteArrayResource resource = new ByteArrayResource(corruptBytes) {
            @Override
            public String getFilename() {
                return "corrupt.zip";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:8089/api/student/exams/" + exam.getId() + "/submissions",
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Recovery Case: Idempotency ensures replaying completed job does not duplicate execution")
    void testIdempotency_DuplicateJobExecutionSafe() {
        Submission completedSub = submissionRepository.save(Submission.builder()
                .exam(exam)
                .student(student1)
                .status("COMPLETED")
                .executionStatus("SUCCESS")
                .exitCode(0)
                .sourceCode("public class Done {}")
                .submissionType("SINGLE_FILE")
                .build());

        HttpHeaders internalHeaders = new HttpHeaders();
        internalHeaders.set("X-Internal-Token", "codeassess-internal-secret-2026");

        // Call internal grading API directly on completed submission
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "http://localhost:8089/api/internal/submissions/" + completedSub.getId() + "/process-grading",
                new HttpEntity<>(null, internalHeaders), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("message")).isEqualTo("Already processed");
        assertThat(res.getBody().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @Order(7)
    @DisplayName("Recovery Case: SubmissionRecoverySweeper recovers stale PENDING submissions")
    void testSweeper_RecoversStalePendingSubmissions() {
        // Create a submission and update its timestamp in DB to 5 minutes ago so it is
        // stale (>60s)
        Submission stuckSub = submissionRepository.save(Submission.builder()
                .exam(exam)
                .student(student1)
                .status("PENDING")
                .sourceCode(
                        "public class Main { public static void main(String[] args) { System.out.println(\"Sweeper recovered\"); } }")
                .submissionType("SINGLE_FILE")
                .build());

        jdbcTemplate.update("UPDATE submissions SET created_at = DATE_SUB(NOW(), INTERVAL 5 MINUTE) WHERE id = ?",
                stuckSub.getId());

        // Invoke recovery sweeper
        submissionRecoverySweeper.recoverPendingSubmissions();

        // Verify the sweeper dispatched it to BullMQ and the worker completed it
        await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            Submission recovered = submissionRepository.findById(stuckSub.getId()).orElse(null);
            return recovered != null && "COMPLETED".equals(recovered.getStatus());
        });

        Submission finalSub = submissionRepository.findById(stuckSub.getId()).orElseThrow();
        assertThat(finalSub.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalSub.getExecutionStatus()).isEqualTo("SUCCESS");
        assertThat(finalSub.getStdout()).contains("Sweeper recovered");
    }
}
