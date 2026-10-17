package com.codeassess.plagiarism.controller;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.SimilarityPair;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real MySQL Integration Test verifying Faculty Adjudication Persistence
 * against actual database schema, Hibernate entity mapping, and controller layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TeacherPlagiarismAdjudicationIntegrationTest {

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

    private User teacherA;
    private User teacherB;
    private User student1;
    private User student2;
    private SimilarityPair pair;

    @BeforeEach
    public void setup() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        teacherA = userRepository.save(User.builder()
                .email("teacherA_" + uid + "@fpt.edu.vn")
                .fullName("Teacher Alpha " + uid)
                .passwordHash("hashed")
                .role(Role.TEACHER)
                .build());

        teacherB = userRepository.save(User.builder()
                .email("teacherB_" + uid + "@fpt.edu.vn")
                .fullName("Teacher Beta " + uid)
                .passwordHash("hashed")
                .role(Role.TEACHER)
                .build());

        student1 = userRepository.save(User.builder()
                .email("student1_" + uid + "@fpt.edu.vn")
                .fullName("Student One " + uid)
                .passwordHash("hashed")
                .role(Role.STUDENT)
                .build());

        student2 = userRepository.save(User.builder()
                .email("student2_" + uid + "@fpt.edu.vn")
                .fullName("Student Two " + uid)
                .passwordHash("hashed")
                .role(Role.STUDENT)
                .build());

        CourseClass courseClass = courseClassRepository.save(CourseClass.builder()
                .name("PRO192-" + uid)
                .code("PR" + uid)
                .semester("SP25")
                .teacher(teacherA)
                .build());

        Exam exam = examRepository.save(Exam.builder()
                .title("Midterm Java-" + uid)
                .status("ACTIVE")
                .courseClass(courseClass)
                .build());

        Submission sub1 = submissionRepository.save(Submission.builder()
                .exam(exam)
                .student(student1)
                .status("SUBMITTED")
                .sourceCode("public class A { void foo() {} }")
                .build());

        Submission sub2 = submissionRepository.save(Submission.builder()
                .exam(exam)
                .student(student2)
                .status("SUBMITTED")
                .sourceCode("public class B { void foo() {} }")
                .build());

        pair = similarityPairRepository.save(SimilarityPair.builder()
                .exam(exam)
                .submissionA(sub1)
                .submissionB(sub2)
                .similarityRate(new BigDecimal("84.50"))
                .sharedHashesCount(8)
                .isFlagged(true)
                .adjudicationStatus("PENDING")
                .build());
    }

    @Test
    public void testAdjudicationPersistedToDatabaseAndReadBackViaApi() throws Exception {
        // 1. Act: Teacher Alpha posts adjudication decision
        String requestJson = """
            {
                "status": "FLAG_CONFIRMED",
                "notes": "Reviewed in lab session: structural isomorphism in foo() method."
            }
            """;

        mockMvc.perform(post("/api/teacher/plagiarism/" + pair.getId() + "/adjudicate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairId").value(pair.getId().toString()))
                .andExpect(jsonPath("$.status").value("FLAG_CONFIRMED"))
                .andExpect(jsonPath("$.notes").value("Reviewed in lab session: structural isomorphism in foo() method."))
                .andExpect(jsonPath("$.adjudicatedBy").value(teacherA.getFullName()));

        // 2. Assert directly in Database: Verify MySQL row persistence
        SimilarityPair reloaded = similarityPairRepository.findById(pair.getId()).orElseThrow();
        assertEquals("FLAG_CONFIRMED", reloaded.getAdjudicationStatus(), "Status must be updated in MySQL");
        assertEquals("Reviewed in lab session: structural isomorphism in foo() method.", reloaded.getAdjudicationNotes());
        assertNotNull(reloaded.getAdjudicatedBy(), "Reviewer must be persisted in MySQL");
        assertEquals(teacherA.getId(), reloaded.getAdjudicatedBy().getId(), "Reviewer must match Teacher Alpha");
        assertNotNull(reloaded.getAdjudicatedAt(), "Timestamp must be recorded in MySQL");
        assertTrue(reloaded.getIsFlagged(), "AST is_flagged must remain unchanged by faculty adjudication");

        // 3. Assert Read-Back via GET /api/teacher/plagiarism/{id}/compare
        mockMvc.perform(get("/api/teacher/plagiarism/" + pair.getId() + "/compare")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairId").value(pair.getId().toString()))
                .andExpect(jsonPath("$.adjudicationStatus").value("FLAG_CONFIRMED"))
                .andExpect(jsonPath("$.adjudicationNotes").value("Reviewed in lab session: structural isomorphism in foo() method."))
                .andExpect(jsonPath("$.adjudicatedBy").value(teacherA.getFullName()));

        // 4. Assert Read-Back via GET /api/teacher/plagiarism/{id}
        mockMvc.perform(get("/api/teacher/plagiarism/" + pair.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value(pair.getId().toString()))
                .andExpect(jsonPath("$.pair.adjudicationStatus").value("FLAG_CONFIRMED"))
                .andExpect(jsonPath("$.pair.adjudicationNotes").value("Reviewed in lab session: structural isomorphism in foo() method."))
                .andExpect(jsonPath("$.pair.adjudicatedBy").value(teacherA.getFullName()));
    }

    @Test
    public void testAdjudicationUnauthorizedTeacherBlockedBy404Masking() throws Exception {
        // Teacher Beta attempts to adjudicate Teacher Alpha's pair
        String requestJson = """
            {
                "status": "FALSE_POSITIVE",
                "notes": "Attempting illegal override."
            }
            """;

        mockMvc.perform(post("/api/teacher/plagiarism/" + pair.getId() + "/adjudicate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherB.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound()); // 404 resource masking

        // Verify DB state was NOT modified
        SimilarityPair unchanged = similarityPairRepository.findById(pair.getId()).orElseThrow();
        assertEquals("PENDING", unchanged.getAdjudicationStatus(), "DB status must remain PENDING");
        assertNull(unchanged.getAdjudicatedBy(), "No reviewer should be assigned");
    }

    @Test
    public void testAdjudicationInvalidStatusReturns400() throws Exception {
        String invalidJson = """
            {
                "status": "ARBITRARY_STATUS",
                "notes": "Testing invalid status rejection."
            }
            """;

        mockMvc.perform(post("/api/teacher/plagiarism/" + pair.getId() + "/adjudicate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacherA.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
