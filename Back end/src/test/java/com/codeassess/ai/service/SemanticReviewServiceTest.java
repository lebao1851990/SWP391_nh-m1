package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.*;
import com.codeassess.ai.exception.AiIntegrationException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemanticReviewServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private DockerSandboxService dockerSandboxService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SemanticReviewService semanticReviewService;

    private Submission submission;
    private User student;
    private Exam exam;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId("student-1");

        exam = new Exam();
        exam.setId("exam-1");
        exam.setStatus("completed");

        submission = Submission.builder()
                .id("sub-1")
                .student(student)
                .exam(exam)
                .sourceCode("public class Solution {\n    public static void main(String[] args) {\n        int x = 10;\n    }\n}")
                .build();
    }

    @Test
    @DisplayName("Student cannot run Semantic Review during active exam")
    void testActiveExam_BlocksStudentReview() {
        exam.setStatus("active");
        when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1"))
                .thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> semanticReviewService.reviewForStudent("sub-1", "student-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Semantic Code Review is not permitted during an active examination");
    }

    @Test
    @DisplayName("Submission not found throws ResourceNotFoundException")
    void testSubmissionNotFound_ThrowsException() {
        when(submissionRepository.findByIdAndStudent_Id("invalid-sub", "student-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> semanticReviewService.reviewForStudent("invalid-sub", "student-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Review succeeds and caches into submission instructorFeedback")
    void testReviewSucceeds_AndPersistsCache() {
        when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1"))
                .thenReturn(Optional.of(submission));

        SemanticReviewResponse mockAiResponse = SemanticReviewResponse.builder()
                .summary("Overall good code quality")
                .cleanCodeScore(BigDecimal.valueOf(8.50))
                .issues(List.of(
                        CodeReviewIssueDto.builder()
                                .line(3)
                                .severity(ReviewIssueSeverity.INFO)
                                .category(ReviewIssueCategory.READABILITY_AND_NAMING)
                                .message("Variable 'x' is too short")
                                .suggestion("Rename to descriptive noun")
                                .build(),
                        CodeReviewIssueDto.builder()
                                .line(999) // Out of bounds line number
                                .severity(ReviewIssueSeverity.WARNING)
                                .category(ReviewIssueCategory.COMPLEXITY_AND_MAINTAINABILITY)
                                .message("General method length warning")
                                .build()
                ))
                .build();

        when(aiClient.generateStructuredOutput(anyString(), anyString(), eq(SemanticReviewResponse.class)))
                .thenReturn(mockAiResponse);

        SemanticReviewResponse result = semanticReviewService.reviewForStudent("sub-1", "student-1");

        assertThat(result.getCleanCodeScore()).isEqualByComparingTo("8.50");
        assertThat(result.getIssues()).hasSize(2);
        assertThat(result.getIssues().get(0).getLine()).isEqualTo(3);
        assertThat(result.getIssues().get(1).getLine()).isNull(); // out-of-bound line safely reset to null

        verify(submissionRepository).save(submission);
        assertThat(submission.getInstructorFeedback()).contains("<!--SEMANTIC_REVIEW_JSON-->");
    }

    @Test
    @DisplayName("Cached review is returned without calling AI client again")
    void testCachedReview_ReturnsImmediately() {
        submission.setInstructorFeedback("Great effort!\n\n<!--SEMANTIC_REVIEW_JSON-->\n{\"summary\":\"Cached summary\",\"cleanCodeScore\":9.0,\"issues\":[]}");

        when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1"))
                .thenReturn(Optional.of(submission));

        SemanticReviewResponse result = semanticReviewService.reviewForStudent("sub-1", "student-1");

        assertThat(result.getSummary()).isEqualTo("Cached summary");
        assertThat(result.getCleanCodeScore()).isEqualByComparingTo("9.0");
        verifyNoInteractions(aiClient);
    }

    @Test
    @DisplayName("AI Integration Exception is propagated safely")
    void testAiProviderError_PropagatesException() {
        when(submissionRepository.findByIdAndStudent_Id("sub-1", "student-1"))
                .thenReturn(Optional.of(submission));

        when(aiClient.generateStructuredOutput(anyString(), anyString(), eq(SemanticReviewResponse.class)))
                .thenThrow(new AiIntegrationException("Gemini quota exceeded"));

        assertThatThrownBy(() -> semanticReviewService.reviewForStudent("sub-1", "student-1"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessageContaining("Gemini quota exceeded");
    }
}
