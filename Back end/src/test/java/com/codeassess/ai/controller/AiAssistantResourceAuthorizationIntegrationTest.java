package com.codeassess.ai.controller;

import com.codeassess.ai.client.AiClient;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AiAssistantResourceAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseClassRepository courseClassRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SimilarityPairRepository similarityPairRepository;

    @Autowired
    private PlagiarismEvidenceRepository evidenceRepository;

    @MockBean
    private AiClient aiClient;

    private User teacherA;
    private User teacherB;
    private SimilarityPair pairA;
    private SimilarityPair pairB;
    private PlagiarismEvidence evidenceA1;
    private PlagiarismEvidence evidenceB1;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        teacherA = userRepository.save(User.builder()
                .email("teachera_" + uid + "@fpt.edu.vn")
                .passwordHash("hash")
                .fullName("Teacher A")
                .role(Role.TEACHER)
                .build());

        teacherB = userRepository.save(User.builder()
                .email("teacherb_" + uid + "@fpt.edu.vn")
                .passwordHash("hash")
                .fullName("Teacher B")
                .role(Role.TEACHER)
                .build());

        User student1 = userRepository.save(User.builder().email("s1_" + uid + "@fpt.edu.vn").passwordHash("hash").role(Role.STUDENT).build());
        User student2 = userRepository.save(User.builder().email("s2_" + uid + "@fpt.edu.vn").passwordHash("hash").role(Role.STUDENT).build());
        User student3 = userRepository.save(User.builder().email("s3_" + uid + "@fpt.edu.vn").passwordHash("hash").role(Role.STUDENT).build());
        User student4 = userRepository.save(User.builder().email("s4_" + uid + "@fpt.edu.vn").passwordHash("hash").role(Role.STUDENT).build());

        CourseClass classA = courseClassRepository.save(CourseClass.builder().name("Class A " + uid).code("CLA_" + uid).teacher(teacherA).build());
        CourseClass classB = courseClassRepository.save(CourseClass.builder().name("Class B " + uid).code("CLB_" + uid).teacher(teacherB).build());

        Exam examA = examRepository.save(Exam.builder().title("Exam A").courseClass(classA).build());
        Exam examB = examRepository.save(Exam.builder().title("Exam B").courseClass(classB).build());

        Submission subA1 = submissionRepository.save(Submission.builder().exam(examA).student(student1).status("GRADED").sourceCode("class A1 { void test() { System.out.println(\"A1\"); } }").build());
        Submission subA2 = submissionRepository.save(Submission.builder().exam(examA).student(student2).status("GRADED").sourceCode("class A2 { void test() { System.out.println(\"A2\"); } }").build());

        Submission subB1 = submissionRepository.save(Submission.builder().exam(examB).student(student3).status("GRADED").sourceCode("class B1 { void test() { System.out.println(\"B1\"); } }").build());
        Submission subB2 = submissionRepository.save(Submission.builder().exam(examB).student(student4).status("GRADED").sourceCode("class B2 { void test() { System.out.println(\"B2\"); } }").build());

        pairA = similarityPairRepository.save(SimilarityPair.builder()
                .exam(examA)
                .submissionA(subA1)
                .submissionB(subA2)
                .similarityRate(new BigDecimal("88.00"))
                .sharedHashesCount(5)
                .isFlagged(true)
                .build());

        pairB = similarityPairRepository.save(SimilarityPair.builder()
                .exam(examB)
                .submissionA(subB1)
                .submissionB(subB2)
                .similarityRate(new BigDecimal("92.00"))
                .sharedHashesCount(6)
                .isFlagged(true)
                .build());

        evidenceA1 = evidenceRepository.save(PlagiarismEvidence.builder()
                .similarityPair(pairA)
                .evidenceType("MethodDeclaration")
                .description("Matching test() method structure")
                .startLineA(1).endLineA(1)
                .startLineB(1).endLineB(1)
                .build());

        evidenceB1 = evidenceRepository.save(PlagiarismEvidence.builder()
                .similarityPair(pairB)
                .evidenceType("MethodDeclaration")
                .description("Matching test() method in Class B")
                .startLineA(1).endLineA(1)
                .startLineB(1).endLineB(1)
                .build());

        when(aiClient.generateCodeReview(anyString())).thenReturn("AI Code Review: Structurally similar AST subtree.");
    }

    @Test
    void teacherA_CallingReviewOnOwnClassPair_Success() throws Exception {
        String payload = String.format("{\"evidenceId\":\"%s\"}", evidenceA1.getId());

        mockMvc.perform(post("/api/teacher/plagiarism/" + pairA.getId() + "/ai/code-review")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis").value("AI Code Review: Structurally similar AST subtree."));
    }

    @Test
    void teacherA_CallingReviewOnClassB_ReturnsNotFound() throws Exception {
        String payload = String.format("{\"evidenceId\":\"%s\"}", evidenceB1.getId());

        mockMvc.perform(post("/api/teacher/plagiarism/" + pairB.getId() + "/ai/code-review")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.sourceCode").doesNotExist())
                .andExpect(jsonPath("$.evidence").doesNotExist());
    }

    @Test
    void teacherA_PassingClassBEvidenceWithClassAPair_ReturnsNotFound() throws Exception {
        // Cross-pair evidence injection: pairA belongs to teacherA, but evidenceB1 belongs to pairB
        String payload = String.format("{\"evidenceId\":\"%s\"}", evidenceB1.getId());

        mockMvc.perform(post("/api/teacher/plagiarism/" + pairA.getId() + "/ai/code-review")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Evidence does not belong to the requested similarity pair."))
                .andExpect(jsonPath("$.sourceCode").doesNotExist());
    }

    @Test
    void teacherA_CallingReviewOnNonExistentPair_ReturnsNotFound() throws Exception {
        String payload = String.format("{\"evidenceId\":\"%s\"}", evidenceA1.getId());

        mockMvc.perform(post("/api/teacher/plagiarism/99999999/ai/code-review")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.sourceCode").doesNotExist());
    }

    @Test
    void teacherA_CallingReviewWithNonExistentEvidence_ReturnsNotFound() throws Exception {
        String payload = "{\"evidenceId\":\"00000000-0000-0000-0000-000000000000\"}";

        mockMvc.perform(post("/api/teacher/plagiarism/" + pairA.getId() + "/ai/code-review")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Evidence not found."))
                .andExpect(jsonPath("$.sourceCode").doesNotExist());
    }
}
