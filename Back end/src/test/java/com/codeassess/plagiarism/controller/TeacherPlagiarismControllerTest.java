package com.codeassess.plagiarism.controller;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.dto.CodeComparisonDataDto;
import com.codeassess.plagiarism.dto.PlagiarismRecordDto;
import com.codeassess.plagiarism.dto.SimilarityPairDto;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.mapper.PlagiarismMapper;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.security.CustomUserDetailsService;
import com.codeassess.security.SecurityConfig;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherPlagiarismController.class)
@Import(SecurityConfig.class)
public class TeacherPlagiarismControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SimilarityPairRepository similarityPairRepository;

    @MockBean
    private PlagiarismEvidenceRepository evidenceRepository;

    @MockBean
    private PlagiarismMapper mapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private User teacherA;
    private User teacherB;
    private SimilarityPair pairA;
    private SimilarityPair pairB;
    private SimilarityPairDto pairDtoA;

    @BeforeEach
    public void setup() {
        teacherA = User.builder().id("teacher-a-id").email("teachera@fpt.edu.vn").role(Role.TEACHER).build();
        teacherB = User.builder().id("teacher-b-id").email("teacherb@fpt.edu.vn").role(Role.TEACHER).build();

        CourseClass classA = CourseClass.builder().id("class-a").name("Class A").teacher(teacherA).build();
        CourseClass classB = CourseClass.builder().id("class-b").name("Class B").teacher(teacherB).build();

        Exam examA = Exam.builder().id("exam-a").title("Exam A").courseClass(classA).build();
        Exam examB = Exam.builder().id("exam-b").title("Exam B").courseClass(classB).build();

        pairA = SimilarityPair.builder()
                .id(101L)
                .exam(examA)
                .similarityRate(new BigDecimal("85.50"))
                .sharedHashesCount(12)
                .isFlagged(true)
                .build();

        pairB = SimilarityPair.builder()
                .id(202L)
                .exam(examB)
                .similarityRate(new BigDecimal("92.00"))
                .sharedHashesCount(20)
                .isFlagged(true)
                .build();

        pairDtoA = SimilarityPairDto.builder()
                .pairId("101")
                .similarityRate(new BigDecimal("85.50"))
                .isFlagged(true)
                .build();
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void listAllPlagiarismPairs_ReturnsOnlyTeacherPairs() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findActiveByTeacherId("teacher-a-id")).thenReturn(Collections.singletonList(pairA));
        when(mapper.toPairDto(pairA)).thenReturn(pairDtoA);

        mockMvc.perform(get("/api/teacher/plagiarism"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pairId").value("101"))
                .andExpect(jsonPath("$[0].similarityRate").value(85.50))
                .andExpect(jsonPath("$[0].flagged").value(true));

        verify(similarityPairRepository).findActiveByTeacherId("teacher-a-id");
        verify(similarityPairRepository, never()).findAll();
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void listAllPlagiarismPairs_ExcludesOtherTeacherPairs() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findActiveByTeacherId("teacher-a-id")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/teacher/plagiarism"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(similarityPairRepository).findActiveByTeacherId("teacher-a-id");
        verify(similarityPairRepository, never()).findAll();
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void getPlagiarismDetail_Success() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findById(101L)).thenReturn(Optional.of(pairA));
        when(evidenceRepository.findBySimilarityPair_Id(101L)).thenReturn(Collections.emptyList());

        PlagiarismRecordDto recordDto = PlagiarismRecordDto.builder()
                .recordId("101")
                .build();
        when(mapper.toRecordDto(eq(pairA), any())).thenReturn(recordDto);

        mockMvc.perform(get("/api/teacher/plagiarism/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value("101"));
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void getPlagiarismDetail_OtherTeacherPair_ReturnsNotFound() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findById(202L)).thenReturn(Optional.of(pairB)); // pairB belongs to teacherB

        mockMvc.perform(get("/api/teacher/plagiarism/202"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void getComparison_OtherTeacherPair_ReturnsNotFound() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findById(202L)).thenReturn(Optional.of(pairB)); // pairB belongs to teacherB

        mockMvc.perform(get("/api/teacher/plagiarism/202/compare"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void getComparison_Success() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findById(101L)).thenReturn(Optional.of(pairA));
        when(evidenceRepository.findBySimilarityPair_Id(101L)).thenReturn(Collections.emptyList());

        CodeComparisonDataDto comparisonDto = CodeComparisonDataDto.builder()
                .pairId("101")
                .build();
        when(mapper.toComparisonDto(eq(pairA), any())).thenReturn(comparisonDto);

        mockMvc.perform(get("/api/teacher/plagiarism/101/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairId").value("101"));
    }

    @Test
    @WithMockUser(username = "studenta@fpt.edu.vn", roles = "STUDENT")
    public void studentCannotCallTeacherPlagiarism_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/teacher/plagiarism"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void unauthenticatedCannotCallTeacherPlagiarism_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/teacher/plagiarism"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void adjudicatePair_Success() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findByIdAndTeacherIdForUpdate(101L, "teacher-a-id")).thenReturn(Optional.of(pairA));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/teacher/plagiarism/101/adjudicate")
                        .contentType("application/json")
                        .content("{\"status\":\"FLAG_CONFIRMED\",\"notes\":\"Confirmed plagiarism after review.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairId").value("101"))
                .andExpect(jsonPath("$.status").value("FLAG_CONFIRMED"))
                .andExpect(jsonPath("$.notes").value("Confirmed plagiarism after review."))
                .andExpect(jsonPath("$.adjudicatedBy").value("teachera@fpt.edu.vn"));

        verify(similarityPairRepository).save(pairA);
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void adjudicatePair_OtherTeacherPair_ReturnsNotFound() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findByIdAndTeacherIdForUpdate(202L, "teacher-a-id")).thenReturn(Optional.empty());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/teacher/plagiarism/202/adjudicate")
                        .contentType("application/json")
                        .content("{\"status\":\"FALSE_POSITIVE\",\"notes\":\"Shared scaffolding.\"}"))
                .andExpect(status().isNotFound());

        verify(similarityPairRepository, never()).save(pairB);
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void adjudicatePair_InvalidStatus_ReturnsBadRequest() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findByIdAndTeacherIdForUpdate(101L, "teacher-a-id")).thenReturn(Optional.of(pairA));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/teacher/plagiarism/101/adjudicate")
                        .contentType("application/json")
                        .content("{\"status\":\"NOT_A_REAL_STATUS\",\"notes\":\"Some note\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "teachera@fpt.edu.vn", roles = "TEACHER")
    public void adjudicatePair_ExceedingNotesLength_ReturnsBadRequest() throws Exception {
        when(userRepository.findByEmail("teachera@fpt.edu.vn")).thenReturn(Optional.of(teacherA));
        when(similarityPairRepository.findByIdAndTeacherIdForUpdate(101L, "teacher-a-id")).thenReturn(Optional.of(pairA));

        String hugeNote = "a".repeat(2005);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/teacher/plagiarism/101/adjudicate")
                        .contentType("application/json")
                        .content("{\"status\":\"FLAG_CONFIRMED\",\"notes\":\"" + hugeNote + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "studenta@fpt.edu.vn", roles = "STUDENT")
    public void adjudicatePair_StudentRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/teacher/plagiarism/101/adjudicate")
                        .contentType("application/json")
                        .content("{\"status\":\"FLAG_CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void adjudicatePair_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/teacher/plagiarism/101/adjudicate")
                        .contentType("application/json")
                        .content("{\"status\":\"FLAG_CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
