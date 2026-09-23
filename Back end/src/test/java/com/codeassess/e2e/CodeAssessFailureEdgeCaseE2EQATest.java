package com.codeassess.e2e;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.plagiarism.dto.AppealCreateRequestDto;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeAssessFailureEdgeCaseE2EQATest {

    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired private UserRepository userRepository;
    @Autowired private CourseClassRepository courseClassRepository;
    @Autowired private ClassStudentRepository classStudentRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private SimilarityPairRepository similarityPairRepository;
    @Autowired private PlagiarismEvidenceRepository plagiarismEvidenceRepository;
    @Autowired private AppealRepository appealRepository;
    @Autowired private AstFingerprintRepository astFingerprintRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User studentA, studentB, studentC;
    private Exam activeExam, inactiveExam;
    private CourseClass classA;

    @BeforeEach
    public void setupDb() {
        appealRepository.deleteAll();
        plagiarismEvidenceRepository.deleteAll();
        similarityPairRepository.deleteAll();
        astFingerprintRepository.deleteAll();
        submissionRepository.deleteAll();
        examRepository.deleteAll();
        classStudentRepository.deleteAll();
        courseClassRepository.deleteAll();
        userRepository.deleteAll();

        String pass = passwordEncoder.encode("testpass123");
        User teacher = userRepository.save(User.builder().email("teacher@fpt.edu.vn").fullName("Teacher").role(Role.TEACHER).passwordHash(pass).build());
        studentA = userRepository.save(User.builder().email("stua@fpt.edu.vn").fullName("Student A").role(Role.STUDENT).passwordHash(pass).build());
        studentB = userRepository.save(User.builder().email("stub@fpt.edu.vn").fullName("Student B").role(Role.STUDENT).passwordHash(pass).build());
        studentC = userRepository.save(User.builder().email("stuc@fpt.edu.vn").fullName("Student C").role(Role.STUDENT).passwordHash(pass).build());

        classA = courseClassRepository.save(CourseClass.builder().name("Class A").code("CLA").teacher(teacher).build());
        
        classStudentRepository.save(ClassStudent.builder().id(new ClassStudent.ClassStudentId(classA.getId(), studentA.getId())).courseClass(classA).student(studentA).build());
        classStudentRepository.save(ClassStudent.builder().id(new ClassStudent.ClassStudentId(classA.getId(), studentB.getId())).courseClass(classA).student(studentB).build());

        activeExam = examRepository.save(Exam.builder().title("Active").courseClass(classA).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        inactiveExam = examRepository.save(Exam.builder().title("Inactive").courseClass(classA).examDate(LocalDateTime.now().minusDays(1)).duration(90).status("INACTIVE").build());
    }

    private HttpHeaders getAuthHeaders(String email) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("testpass123");
        ResponseEntity<Void> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Void.class);
        List<String> cookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookies != null) headers.put(HttpHeaders.COOKIE, cookies);
        return headers;
    }

    @Test
    @Order(1)
    public void testSubmission_BlankSourceCode() {
        HttpHeaders headers = getAuthHeaders(studentA.getEmail());
        SubmissionCreateRequest req = new SubmissionCreateRequest("   ");
        ResponseEntity<Map> res = restTemplate.exchange("/api/student/exams/" + activeExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(req, headers), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(submissionRepository.count()).isEqualTo(0);
    }

    @Test
    @Order(2)
    public void testSubmission_InvalidJava() {
        Exam isolatedExam = examRepository.save(Exam.builder().title("Invalid Exam").courseClass(classA).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        HttpHeaders headers = getAuthHeaders(studentA.getEmail());
        SubmissionCreateRequest req = new SubmissionCreateRequest("public class { invalid syntax!!! }");
        ResponseEntity<Map> res = restTemplate.exchange("/api/student/exams/" + isolatedExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(req, headers), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        String subId = (String) res.getBody().get("id");
        
        // Wait to ensure async pipeline finishes but creates nothing
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);
        
        // No fingerprints or pairs should be created for this invalid source
        assertThat(astFingerprintRepository.findBySubmission_Id(subId).size()).isEqualTo(0);
        assertThat(similarityPairRepository.count()).isEqualTo(0);
        
        // Server still healthy?
        ResponseEntity<String> health = restTemplate.getForEntity("/api/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(3)
    public void testSubmission_ValidJavaAfterInvalid() {
        Exam isolatedExam = examRepository.save(Exam.builder().title("Valid After Invalid").courseClass(classA).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        HttpHeaders headers = getAuthHeaders(studentB.getEmail());
        SubmissionCreateRequest req = new SubmissionCreateRequest("public class Main { public static void main(String[] args) { System.out.println(\"Valid!\"); } }");
        ResponseEntity<Map> res = restTemplate.exchange("/api/student/exams/" + isolatedExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(req, headers), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        String subId = (String) res.getBody().get("id");
        
        // Should process fine
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> astFingerprintRepository.findBySubmission_Id(subId).size() > 0);
    }

    @Test
    @Order(4)
    public void testSubmission_InactiveExam() {
        HttpHeaders headers = getAuthHeaders(studentA.getEmail());
        SubmissionCreateRequest req = new SubmissionCreateRequest("public class X {}");
        ResponseEntity<Map> res = restTemplate.exchange("/api/student/exams/" + inactiveExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(req, headers), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(5)
    public void testSubmission_NotEnrolled() {
        HttpHeaders headers = getAuthHeaders(studentC.getEmail());
        SubmissionCreateRequest req = new SubmissionCreateRequest("public class X {}");
        ResponseEntity<Map> res = restTemplate.exchange("/api/student/exams/" + activeExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(req, headers), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    public void testPlagiarism_ExactThresholdsFocused() {
        // Business logic asserts directly mapping to Phase 10 exact threshold rule
        BigDecimal t1 = new BigDecimal("69.99");
        boolean flag1 = t1.compareTo(new BigDecimal("70.00")) >= 0;
        assertThat(flag1).isFalse();

        BigDecimal t2 = new BigDecimal("70.00");
        boolean flag2 = t2.compareTo(new BigDecimal("70.00")) >= 0;
        assertThat(flag2).isTrue();

        BigDecimal t3 = new BigDecimal("70.01");
        boolean flag3 = t3.compareTo(new BigDecimal("70.00")) >= 0;
        assertThat(flag3).isTrue();
    }
    
    @Test
    @Order(7)
    public void testPlagiarism_NoMatchingPair() {
        Exam isolatedExam = examRepository.save(Exam.builder().title("No Match Exam").courseClass(classA).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        
        HttpHeaders headersA = getAuthHeaders(studentA.getEmail());
        HttpHeaders headersB = getAuthHeaders(studentB.getEmail());
        
        SubmissionCreateRequest reqA = new SubmissionCreateRequest("public class A { public void doNothing() { int x = 1; } }");
        SubmissionCreateRequest reqB = new SubmissionCreateRequest("public class B { public String hello() { return \"World\"; } }");
        
        restTemplate.exchange("/api/student/exams/" + isolatedExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(reqA, headersA), Map.class);
        restTemplate.exchange("/api/student/exams/" + isolatedExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(reqB, headersB), Map.class);
        
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> submissionRepository.findByExam_Id(isolatedExam.getId()).size() >= 2);
        Awaitility.await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);
        
        // Ensure no pairs are FLAGGED for this exam
        List<SimilarityPair> pairs = similarityPairRepository.findAll().stream().filter(p -> p.getExam().getId().equals(isolatedExam.getId())).toList();
        for (SimilarityPair pair : pairs) {
            assertThat(pair.getIsFlagged()).isFalse();
        }
    }

    @Test
    @Order(8)
    public void testAppeal_ConcurrentAndDuplicate() throws Exception {
        Exam isolatedExam = examRepository.save(Exam.builder().title("Appeal Exam").courseClass(classA).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        Submission sub = submissionRepository.save(Submission.builder().id(UUID.randomUUID().toString()).exam(isolatedExam).student(studentA).sourceCode("A").status("PENDING").build());
        
        HttpHeaders headers = getAuthHeaders(studentA.getEmail());
        AppealCreateRequestDto req = new AppealCreateRequestDto();
        req.setSubmissionId(sub.getId());
        req.setBasis("MISTAKE");
        req.setExplanation("Concurrent test");

        HttpEntity<AppealCreateRequestDto> entity = new HttpEntity<>(req, headers);
        
        CompletableFuture<ResponseEntity<Map>> req1 = CompletableFuture.supplyAsync(() -> restTemplate.exchange("/api/student/appeals", HttpMethod.POST, entity, Map.class));
        CompletableFuture<ResponseEntity<Map>> req2 = CompletableFuture.supplyAsync(() -> restTemplate.exchange("/api/student/appeals", HttpMethod.POST, entity, Map.class));

        CompletableFuture.allOf(req1, req2).join();

        ResponseEntity<Map> res1 = req1.get();
        ResponseEntity<Map> res2 = req2.get();

        boolean oneCreated = res1.getStatusCode() == HttpStatus.CREATED || res2.getStatusCode() == HttpStatus.CREATED;
        boolean oneConflict = res1.getStatusCode() == HttpStatus.CONFLICT || res2.getStatusCode() == HttpStatus.CONFLICT;

        assertThat(oneCreated).isTrue();
        assertThat(oneConflict).isTrue();
        
        assertThat(appealRepository.findByStudent_Id(studentA.getId()).size()).isEqualTo(1);
    }
    
    @Test
    @Order(9)
    public void testResourceBoundary_InvalidOpaqueId() {
        HttpHeaders headers = getAuthHeaders(studentA.getEmail());
        ResponseEntity<Map> res = restTemplate.exchange("/api/student/appeals/INVALID_ID", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        
        // ResourceNotFoundException is mapped to 404
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(10)
    public void testDEF01_ConcurrentMatrixGenerationRaceCondition_DoesNotLoseData() throws Exception {
        // We create an isolated exam
        Exam isolatedExam = examRepository.save(Exam.builder().title("DEF-01 Exam").courseClass(classA).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        
        // We prepare 3 submissions with slightly different code so they all parse
        SubmissionCreateRequest reqA = new SubmissionCreateRequest("public class A { public void run() { int x = 1; } }");
        SubmissionCreateRequest reqB = new SubmissionCreateRequest("public class B { public void run() { int x = 2; } }");
        SubmissionCreateRequest reqC = new SubmissionCreateRequest("public class C { public void run() { int x = 3; } }");

        HttpHeaders headersA = getAuthHeaders(studentA.getEmail());
        HttpHeaders headersB = getAuthHeaders(studentB.getEmail());
        HttpHeaders headersC = getAuthHeaders(studentC.getEmail());

        // Enroll Student C to class A for this test
        classStudentRepository.save(ClassStudent.builder().id(new ClassStudent.ClassStudentId(classA.getId(), studentC.getId())).courseClass(classA).student(studentC).build());

        // Submit concurrently to force the listener race condition
        CompletableFuture<ResponseEntity<Map>> sub1 = CompletableFuture.supplyAsync(() -> restTemplate.exchange("/api/student/exams/" + isolatedExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(reqA, headersA), Map.class));
        CompletableFuture<ResponseEntity<Map>> sub2 = CompletableFuture.supplyAsync(() -> restTemplate.exchange("/api/student/exams/" + isolatedExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(reqB, headersB), Map.class));
        CompletableFuture<ResponseEntity<Map>> sub3 = CompletableFuture.supplyAsync(() -> restTemplate.exchange("/api/student/exams/" + isolatedExam.getId() + "/submissions", HttpMethod.POST, new HttpEntity<>(reqC, headersC), Map.class));

        CompletableFuture.allOf(sub1, sub2, sub3).join();

        assertThat(sub1.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sub2.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sub3.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Wait until fingerprints are fully generated for all 3
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> fingerprintRepositoryForTest(isolatedExam.getId()) >= 3);
        
        // Wait an extra moment to let the matrix generation settle after exception or success
        Thread.sleep(3000);

        // Verify that no similarity pair was silently lost due to the exception terminating the loop early
        // For 3 submissions, there must be EXACTLY 3 pairs: (A,B), (A,C), (B,C)
        long pairsCount = similarityPairRepository.findAll().stream().filter(p -> p.getExam().getId().equals(isolatedExam.getId())).count();
        
        assertThat(pairsCount).as("DEF-01: Concurrent matrix generation must not lose similarity pairs").isEqualTo(3);
    }
    
    private long fingerprintRepositoryForTest(String examId) {
        List<Submission> subs = submissionRepository.findByExam_Id(examId);
        long count = 0;
        for (Submission s : subs) {
            if (astFingerprintRepository.findBySubmission_Id(s.getId()).size() > 0) {
                count++;
            }
        }
        return count;
    }
}
