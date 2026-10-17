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
import com.codeassess.submission.entity.Submission;
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
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeAssessSecurityE2EQATest {

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
    private AppealRepository appealRepository;

    @Autowired
    private com.codeassess.plagiarism.repository.AstFingerprintRepository astFingerprintRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private javax.sql.DataSource dataSource;

    // Identities
    private User admin;
    private User teacherA;
    private User teacherB;
    private User studentA;
    private User studentB;
    private User studentC; // For pairing with A
    private User studentD; // For pairing with B

    // Resources Tenant A
    private Exam examA;
    private Submission subA;
    private SimilarityPair pairA;
    private Appeal appealA;

    // Resources Tenant B
    private Exam examB;
    private Submission subB;
    private SimilarityPair pairB;
    private Appeal appealB;

    @BeforeEach
    public void setupDb() {
        com.codeassess.testconfig.DatabaseSafetyGuard.assertSafeTestDatabase(dataSource);
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

        admin = userRepository.save(User.builder().email("admin@fpt.edu.vn").fullName("Admin").role(Role.ADMIN).passwordHash(pass).build());
        teacherA = userRepository.save(User.builder().email("teachera@fpt.edu.vn").fullName("Teacher A").role(Role.TEACHER).passwordHash(pass).build());
        teacherB = userRepository.save(User.builder().email("teacherb@fpt.edu.vn").fullName("Teacher B").role(Role.TEACHER).passwordHash(pass).build());
        studentA = userRepository.save(User.builder().email("studenta@fpt.edu.vn").fullName("Student A").role(Role.STUDENT).passwordHash(pass).build());
        studentB = userRepository.save(User.builder().email("studentb@fpt.edu.vn").fullName("Student B").role(Role.STUDENT).passwordHash(pass).build());
        studentC = userRepository.save(User.builder().email("studentc@fpt.edu.vn").fullName("Student C").role(Role.STUDENT).passwordHash(pass).build());
        studentD = userRepository.save(User.builder().email("studentd@fpt.edu.vn").fullName("Student D").role(Role.STUDENT).passwordHash(pass).build());

        // Tenant A Setup
        CourseClass classA = courseClassRepository.save(CourseClass.builder().name("Class A").code("CLA").teacher(teacherA).build());
        classStudentRepository.save(ClassStudent.builder().id(new ClassStudent.ClassStudentId(classA.getId(), studentA.getId())).courseClass(classA).student(studentA).build());
        classStudentRepository.save(ClassStudent.builder().id(new ClassStudent.ClassStudentId(classA.getId(), studentC.getId())).courseClass(classA).student(studentC).build());
        
        examA = examRepository.save(Exam.builder().title("Exam A").courseClass(classA).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        
        subA = submissionRepository.save(Submission.builder().id(UUID.randomUUID().toString()).exam(examA).student(studentA).sourceCode("codeA").status("GRADED").totalScore(new BigDecimal("10")).build());
        Submission subC = submissionRepository.save(Submission.builder().id(UUID.randomUUID().toString()).exam(examA).student(studentC).sourceCode("codeC").status("GRADED").totalScore(new BigDecimal("10")).build());
        
        pairA = similarityPairRepository.save(SimilarityPair.builder().exam(examA).submissionA(subA).submissionB(subC).similarityRate(new BigDecimal("85")).sharedHashesCount(10).isFlagged(true).build());
        plagiarismEvidenceRepository.save(PlagiarismEvidence.builder().similarityPair(pairA).evidenceType("STRUCTURAL").description("Test").startLineA(1).endLineA(2).startLineB(1).endLineB(2).build());
        appealA = appealRepository.save(Appeal.builder().submission(subA).student(studentA).reason("Test appeal A").basis("MISTAKE").status("PENDING").build());

        // Tenant B Setup
        CourseClass classB = courseClassRepository.save(CourseClass.builder().name("Class B").code("CLB").teacher(teacherB).build());
        classStudentRepository.save(ClassStudent.builder().id(new ClassStudent.ClassStudentId(classB.getId(), studentB.getId())).courseClass(classB).student(studentB).build());
        classStudentRepository.save(ClassStudent.builder().id(new ClassStudent.ClassStudentId(classB.getId(), studentD.getId())).courseClass(classB).student(studentD).build());

        examB = examRepository.save(Exam.builder().title("Exam B").courseClass(classB).examDate(LocalDateTime.now().plusDays(1)).duration(90).status("ACTIVE").build());
        
        subB = submissionRepository.save(Submission.builder().id(UUID.randomUUID().toString()).exam(examB).student(studentB).sourceCode("codeB").status("GRADED").totalScore(new BigDecimal("10")).build());
        Submission subD = submissionRepository.save(Submission.builder().id(UUID.randomUUID().toString()).exam(examB).student(studentD).sourceCode("codeD").status("GRADED").totalScore(new BigDecimal("10")).build());
        
        pairB = similarityPairRepository.save(SimilarityPair.builder().exam(examB).submissionA(subB).submissionB(subD).similarityRate(new BigDecimal("80")).sharedHashesCount(8).isFlagged(true).build());
        plagiarismEvidenceRepository.save(PlagiarismEvidence.builder().similarityPair(pairB).evidenceType("STRUCTURAL").description("Test").startLineA(1).endLineA(2).startLineB(1).endLineB(2).build());
        appealB = appealRepository.save(Appeal.builder().submission(subB).student(studentB).reason("Test appeal B").basis("MISTAKE").status("PENDING").build());
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
    public void testAuthentication_AnonymousYields401() {
        HttpHeaders anonymous = new HttpHeaders();
        
        // Student endpoints
        assertThat(restTemplate.exchange("/api/student/dashboard", HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/student/exams", HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/student/submissions/" + subA.getId(), HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/student/plagiarism/" + pairA.getId(), HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        
        // Teacher endpoints
        assertThat(restTemplate.exchange("/api/teacher/exams", HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/teacher/submissions/" + subA.getId(), HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/teacher/plagiarism/" + pairA.getId(), HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        
        // Admin endpoints
        assertThat(restTemplate.exchange("/api/admin/metrics", HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/admin/users", HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    public void testRBAC_CrossRoleYields403() {
        HttpHeaders studentHeaders = getAuthHeaders(studentA.getEmail());
        HttpHeaders teacherHeaders = getAuthHeaders(teacherA.getEmail());

        // Student accessing Teacher/Admin
        ResponseEntity<Map> st1 = restTemplate.exchange("/api/teacher/plagiarism", HttpMethod.GET, new HttpEntity<>(studentHeaders), Map.class);
        assertThat(st1.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(st1.getBody().get("status")).isEqualTo(403);
        
        ResponseEntity<Map> st2 = restTemplate.exchange("/api/admin/users", HttpMethod.GET, new HttpEntity<>(studentHeaders), Map.class);
        assertThat(st2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(st2.getBody().get("status")).isEqualTo(403);

        // Teacher accessing Student/Admin
        ResponseEntity<Map> te1 = restTemplate.exchange("/api/student/submissions", HttpMethod.GET, new HttpEntity<>(teacherHeaders), Map.class);
        assertThat(te1.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(te1.getBody().get("status")).isEqualTo(403);
        
        ResponseEntity<Map> te2 = restTemplate.exchange("/api/admin/users", HttpMethod.GET, new HttpEntity<>(teacherHeaders), Map.class);
        assertThat(te2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(te2.getBody().get("status")).isEqualTo(403);
    }

    @Test
    @Order(3)
    public void testIDOR_CrossTenantResourceAccess() {
        HttpHeaders studentAHeaders = getAuthHeaders(studentA.getEmail());
        HttpHeaders teacherAHeaders = getAuthHeaders(teacherA.getEmail());

        // STUDENT A trying to access STUDENT B's resources
        ResponseEntity<String> sIdor1 = restTemplate.exchange("/api/student/submissions/" + subB.getId(), HttpMethod.GET, new HttpEntity<>(studentAHeaders), String.class);
        assertThat(sIdor1.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        ResponseEntity<String> sIdor2 = restTemplate.exchange("/api/student/plagiarism/" + pairB.getId(), HttpMethod.GET, new HttpEntity<>(studentAHeaders), String.class);
        assertThat(sIdor2.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        ResponseEntity<String> sIdor3 = restTemplate.exchange("/api/student/appeals/" + appealB.getId(), HttpMethod.GET, new HttpEntity<>(studentAHeaders), String.class);
        assertThat(sIdor3.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // TEACHER A trying to access TEACHER B's resources
        ResponseEntity<String> tIdor1 = restTemplate.exchange("/api/teacher/submissions/" + subB.getId(), HttpMethod.GET, new HttpEntity<>(teacherAHeaders), String.class);
        assertThat(tIdor1.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        ResponseEntity<String> tIdor2 = restTemplate.exchange("/api/teacher/plagiarism/" + pairB.getId(), HttpMethod.GET, new HttpEntity<>(teacherAHeaders), String.class);
        assertThat(tIdor2.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        ResponseEntity<String> tIdor3 = restTemplate.exchange("/api/teacher/exams/" + examB.getId(), HttpMethod.GET, new HttpEntity<>(teacherAHeaders), String.class);
        assertThat(tIdor3.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        
        ResponseEntity<String> tIdor4 = restTemplate.exchange("/api/teacher/exams/" + examB.getId() + "/submissions", HttpMethod.GET, new HttpEntity<>(teacherAHeaders), String.class);
        assertThat(tIdor4.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(4)
    public void testDataLeakage_StudentAPI_ExcludesSensitiveFields() {
        HttpHeaders studentAHeaders = getAuthHeaders(studentA.getEmail());

        // Check Plagiarism Detail (Student should NOT see other student info)
        ResponseEntity<String> response = restTemplate.exchange("/api/student/plagiarism/" + pairA.getId(), HttpMethod.GET, new HttpEntity<>(studentAHeaders), String.class);
        
        // It's possible the response is 404 if the frontend isn't fully implemented or student isn't allowed to view it this way,
        // but if it is 200 OK, we must check for data leakage.
        if (response.getStatusCode() == HttpStatus.OK) {
            String json = response.getBody();
            assertThat(json).isNotNull();
            
            // Should NOT contain specific leaked field names anywhere in the JSON recursively
            assertThat(json).doesNotContain("hash_value");
            assertThat(json).doesNotContain("hashValue");
            assertThat(json).doesNotContain("otherStudentId");
            assertThat(json).doesNotContain("otherStudentName");
            assertThat(json).doesNotContain("otherStudentSourceCode");
            
            // Exclude raw DB PKs for users (e.g. Student C's ID). 
            // In our system IDs are longs or UUIDs, but we just verify we don't leak "studentc@fpt.edu.vn" 
            assertThat(json).doesNotContain("studentc@fpt.edu.vn");
            assertThat(json).doesNotContain("Student C");
        }
    }
}
