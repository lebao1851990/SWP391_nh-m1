package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.AiAnalysisResponse;
import com.codeassess.ai.dto.AiCodeReviewRequest;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private SimilarityPairRepository similarityPairRepository;

    @Mock
    private PlagiarismEvidenceRepository plagiarismEvidenceRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private AiAssistantService aiAssistantService;

    private SimilarityPair pair;
    private PlagiarismEvidence evidence;

    @BeforeEach
    void setUp() {
        User teacher = User.builder().id("teacher-id-123").email("teacher1").build();
        CourseClass aClass = CourseClass.builder().teacher(teacher).build();
        Exam exam = Exam.builder().courseClass(aClass).build();
        Submission subA = Submission.builder().sourceCode("line1\nline2\nline3\nline4\n").build();
        Submission subB = Submission.builder().sourceCode("b1\nb2\nb3\nb4\nb5\n").build();

        pair = SimilarityPair.builder()
                .id(1L)
                .exam(exam)
                .submissionA(subA)
                .submissionB(subB)
                .similarityRate(new BigDecimal("75.50"))
                .sharedHashesCount(10)
                .isFlagged(true)
                .build();

        evidence = PlagiarismEvidence.builder()
                .id("ev123")
                .similarityPair(pair)
                .evidenceType("ForLoop")
                .description("Nested loops match")
                .startLineA(2)
                .endLineA(3)
                .startLineB(2)
                .endLineB(3)
                .build();

        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testGetSimilarityExplanation_Success() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("teacher1");

        when(similarityPairRepository.findById(1L)).thenReturn(Optional.of(pair));
        when(plagiarismEvidenceRepository.findBySimilarityPair_Id(1L)).thenReturn(List.of(evidence));
        when(userRepository.findByEmail("teacher1")).thenReturn(Optional.of(pair.getExam().getCourseClass().getTeacher()));
        when(aiClient.generateSimilarityExplanation(anyString())).thenReturn("AI explanation.");

        AiAnalysisResponse response = aiAssistantService.getSimilarityExplanation(1L);

        assertEquals("AI explanation.", response.analysis());
        verify(aiClient).generateSimilarityExplanation(argThat(prompt -> 
                prompt.contains("75.50%") && prompt.contains("ForLoop") && !prompt.contains("hash_value")
        ));
    }

    @Test
    void testGetSimilarityExplanation_UnauthorizedMaskedAs404() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("otherTeacher");

        when(similarityPairRepository.findById(1L)).thenReturn(Optional.of(pair));
        when(userRepository.findByEmail("otherTeacher")).thenReturn(Optional.of(User.builder().id("other-id").build()));

        assertThrows(ResourceNotFoundException.class, () -> aiAssistantService.getSimilarityExplanation(1L));
    }

    @Test
    void testGetCodeReview_Success() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("teacher1");

        when(similarityPairRepository.findById(1L)).thenReturn(Optional.of(pair));
        when(plagiarismEvidenceRepository.findById("ev123")).thenReturn(Optional.of(evidence));
        when(userRepository.findByEmail("teacher1")).thenReturn(Optional.of(pair.getExam().getCourseClass().getTeacher()));
        when(aiClient.generateCodeReview(anyString())).thenReturn("AI review.");

        AiCodeReviewRequest request = new AiCodeReviewRequest("ev123");
        AiAnalysisResponse response = aiAssistantService.getCodeReview(1L, request);

        assertEquals("AI review.", response.analysis());
        
        // Ensure only lines 2-3 are extracted (0-indexed: line2\nline3)
        verify(aiClient).generateCodeReview(argThat(prompt -> 
                prompt.contains("line2") && prompt.contains("line3") && !prompt.contains("line1") && !prompt.contains("line4")
        ));
    }

    @Test
    void testGetCodeReview_EvidenceDoesNotBelongToPair() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("teacher1");

        SimilarityPair otherPair = SimilarityPair.builder().id(2L).exam(pair.getExam()).build();
        evidence.setSimilarityPair(otherPair);

        when(similarityPairRepository.findById(1L)).thenReturn(Optional.of(pair));
        when(plagiarismEvidenceRepository.findById("ev123")).thenReturn(Optional.of(evidence));
        when(userRepository.findByEmail("teacher1")).thenReturn(Optional.of(pair.getExam().getCourseClass().getTeacher()));

        AiCodeReviewRequest request = new AiCodeReviewRequest("ev123");
        assertThrows(ResourceNotFoundException.class, () -> aiAssistantService.getCodeReview(1L, request));
    }

    @Test
    void testGetCodeReview_TeacherNotAssignedToClass_MaskedAs404() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("teacherB");

        when(similarityPairRepository.findById(1L)).thenReturn(Optional.of(pair));
        when(userRepository.findByEmail("teacherB")).thenReturn(Optional.of(User.builder().id("teacher-b-id").build()));

        AiCodeReviewRequest request = new AiCodeReviewRequest("ev123");
        assertThrows(ResourceNotFoundException.class, () -> aiAssistantService.getCodeReview(1L, request));
    }

    @Test
    void testGetCodeReview_PairNotFound() {
        when(similarityPairRepository.findById(999L)).thenReturn(Optional.empty());

        AiCodeReviewRequest request = new AiCodeReviewRequest("ev123");
        assertThrows(ResourceNotFoundException.class, () -> aiAssistantService.getCodeReview(999L, request));
    }

    @Test
    void testGetCodeReview_EvidenceNotFound() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("teacher1");

        when(similarityPairRepository.findById(1L)).thenReturn(Optional.of(pair));
        when(userRepository.findByEmail("teacher1")).thenReturn(Optional.of(pair.getExam().getCourseClass().getTeacher()));
        when(plagiarismEvidenceRepository.findById("non-existent-ev")).thenReturn(Optional.empty());

        AiCodeReviewRequest request = new AiCodeReviewRequest("non-existent-ev");
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> aiAssistantService.getCodeReview(1L, request));
        assertEquals("Evidence not found.", ex.getMessage());
    }
}
