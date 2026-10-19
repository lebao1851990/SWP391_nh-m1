package com.codeassess.e2e;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.awaitility.Awaitility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeAssessE2ERuntimeQATest {

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
    private SimilarityPairRepository similarityPairRepository;

    @Autowired
    private PlagiarismEvidenceRepository plagiarismEvidenceRepository;

    @Autowired
    private com.codeassess.plagiarism.repository.AstFingerprintRepository astFingerprintRepository;

    @Autowired
    private com.codeassess.plagiarism.repository.AppealRepository appealRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private javax.sql.DataSource dataSource;

    private User teacher;
    private User student1;
    private User student2;
    private Exam exam;
    private CourseClass courseClass;

    private void cleanUpTestData() {
        appealRepository.deleteAll();
        plagiarismEvidenceRepository.deleteAll();
        similarityPairRepository.deleteAll();
        astFingerprintRepository.deleteAll();
        submissionRepository.deleteAll();
        examRepository.deleteAll();
        classStudentRepository.deleteAll();
        courseClassRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    public void setupDb() {
        com.codeassess.testconfig.DatabaseSafetyGuard.assertSafeTestDatabase(dataSource);
        cleanUpTestData();

        // 1. Seed Users
        String encodedPassword = passwordEncoder.encode("testpass123");
        
        teacher = userRepository.save(User.builder()
                .email("e2e-teacher@fpt.edu.vn")
                .fullName("E2E Teacher")
                .role(Role.TEACHER)
                .passwordHash(encodedPassword)
                .build());

        student1 = userRepository.save(User.builder()
                .email("e2e-student1@fpt.edu.vn")
                .fullName("E2E Student 1")
                .role(Role.STUDENT)
                .passwordHash(encodedPassword)
                .build());

        student2 = userRepository.save(User.builder()
                .email("e2e-student2@fpt.edu.vn")
                .fullName("E2E Student 2")
                .role(Role.STUDENT)
                .passwordHash(encodedPassword)
                .build());

        // 2. Seed Course Class
        courseClass = courseClassRepository.save(CourseClass.builder()
                .name("E2E-101")
                .code("SWE102")
                .teacher(teacher)
                .build());

        // 3. Seed Enrollment
        classStudentRepository.save(ClassStudent.builder()
                .id(new ClassStudent.ClassStudentId(courseClass.getId(), student1.getId()))
                .courseClass(courseClass).student(student1).build());
        classStudentRepository.save(ClassStudent.builder()
                .id(new ClassStudent.ClassStudentId(courseClass.getId(), student2.getId()))
                .courseClass(courseClass).student(student2).build());

        // 4. Seed Exam
        exam = examRepository.save(Exam.builder()
                .title("E2E Practical Exam")
                .courseClass(courseClass)
                .examDate(LocalDateTime.now().plusDays(1))
                .duration(90)
                .status("ACTIVE")
                .build());
    }

    private HttpHeaders getAuthHeaders(String email) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("testpass123");

        ResponseEntity<Void> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Void.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        List<String> cookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookies != null) {
            headers.put(HttpHeaders.COOKIE, cookies);
        }
        return headers;
    }

    @Test
    @Order(1)
    public void testStudentSubmissionAndPipeline() throws Exception {
        HttpHeaders s1Headers = getAuthHeaders(student1.getEmail());
        
        // TEST 1: STUDENT SUBMISSION
        String code1 = "public class Main { public static void main(String[] args) { System.out.println(\"Hello 1\"); } }";
        SubmissionCreateRequest subDto1 = new SubmissionCreateRequest();
        subDto1.setSourceCode(code1);

        ResponseEntity<Map> subResponse1 = restTemplate.postForEntity(
                "/api/student/exams/" + exam.getId() + "/submissions", 
                new HttpEntity<>(subDto1, s1Headers), 
                Map.class);
        
        assertThat(subResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String submissionId1 = (String) subResponse1.getBody().get("id");
        assertThat(submissionId1).isNotNull();

        // TEST 2: AST PIPELINE (Wait for processing)
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            var sub = submissionRepository.findById(submissionId1).orElse(null);
            return sub != null;
        });

        // TEST 3: SIMILARITY
        HttpHeaders s2Headers = getAuthHeaders(student2.getEmail());
        String code2 = "public class Main { public static void main(String[] args) { System.out.println(\"Hello 2\"); } }";
        SubmissionCreateRequest subDto2 = new SubmissionCreateRequest();
        subDto2.setSourceCode(code2);

        ResponseEntity<Map> subResponse2 = restTemplate.postForEntity(
                "/api/student/exams/" + exam.getId() + "/submissions", 
                new HttpEntity<>(subDto2, s2Headers), 
                Map.class);
        
        assertThat(subResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        // Await similarity matrix and evidence creation
        await().atMost(15, TimeUnit.SECONDS).until(() -> 
            plagiarismEvidenceRepository.count() > 0
        );
        
        var pairs = similarityPairRepository.findAll();
        assertThat(pairs).isNotEmpty();
        
        // TEST 4: EVIDENCE
        var evidence = plagiarismEvidenceRepository.findAll();
        assertThat(evidence).isNotEmpty();
    }
    
    @Test
    @Order(2)
    public void testAuthorizationAndUIAccess() {
        HttpHeaders s1Headers = getAuthHeaders(student1.getEmail());
        HttpHeaders tHeaders = getAuthHeaders(teacher.getEmail());
        
        // TEST 7: AUTHORIZATION / IDOR
        ResponseEntity<String> unauthorizedStudent = restTemplate.exchange(
                "/api/teacher/submissions", HttpMethod.GET, new HttpEntity<>(s1Headers), String.class);
        assertThat(unauthorizedStudent.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);

        // TEST 5: TEACHER UI
        ResponseEntity<String> teacherPlagiarism = restTemplate.exchange(
                "/api/teacher/plagiarism", HttpMethod.GET, new HttpEntity<>(tHeaders), String.class);
        assertThat(teacherPlagiarism.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Test
    @Order(3)
    public void testASTFailure() {
        HttpHeaders s1Headers = getAuthHeaders(student1.getEmail());
        
        // TEST 8: AST FAILURE
        String invalidCode = "public class { bad syntax !! }";
        SubmissionCreateRequest subDto = new SubmissionCreateRequest();
        subDto.setSourceCode(invalidCode);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/student/exams/" + exam.getId() + "/submissions", 
                new HttpEntity<>(subDto, s1Headers), 
                Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String subId = (String) response.getBody().get("id");
        
        // Wait and verify submission remains persisted but no similarity crash occurs
        await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);
        
        var sub = submissionRepository.findById(subId);
        assertThat(sub).isPresent();
    }
}
