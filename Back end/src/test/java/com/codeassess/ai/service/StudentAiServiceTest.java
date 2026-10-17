package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.StudentAiMode;
import com.codeassess.ai.dto.StudentAiRequest;
import com.codeassess.ai.dto.StudentAiResponse;
import com.codeassess.ai.dto.StudentSandboxAiRequest;
import com.codeassess.ai.exception.AiIntegrationException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAiServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private DockerSandboxService dockerSandboxService;

    @InjectMocks
    private StudentAiService studentAiService;

    private User student;
    private Exam exam;
    private Submission submission;

    @BeforeEach
    void setUp() {
        student = User.builder().id("student-123").email("student@test.com").build();
        exam = Exam.builder().id("exam-456").title("Final Exam").status("completed").build();
        submission = Submission.builder()
                .id("sub-789")
                .student(student)
                .exam(exam)
                .sourceCode("public class Main { public static void main(String[] args) {} }")
                .status("EVALUATED")
                .totalScore(new BigDecimal("85.00"))
                .build();
    }

    @Test
    void testGetSubmissionFeedback_CodeExplanation_Success() {
        when(submissionRepository.findByIdAndStudent_Id("sub-789", "student-123"))
                .thenReturn(Optional.of(submission));
        when(aiClient.generateStudentFeedback(anyString(), anyString()))
                .thenReturn("This is a simple Java program with an entrypoint main method.");

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.CODE_EXPLANATION, null);
        StudentAiResponse response = studentAiService.getSubmissionFeedback("sub-789", "student-123", request);

        assertNotNull(response);
        assertEquals(StudentAiMode.CODE_EXPLANATION, response.mode());
        assertEquals("This is a simple Java program with an entrypoint main method.", response.explanation());
        assertNotNull(response.advisoryNotice());
        assertTrue(response.advisoryNotice().contains("Advisory Notice"));

        // Verify grade/submission state is unchanged
        assertEquals(new BigDecimal("85.00"), submission.getTotalScore());
        assertEquals("EVALUATED", submission.getStatus());
    }

    @Test
    void testGetSubmissionFeedback_CompilerError_WithProvidedOutput() {
        when(submissionRepository.findByIdAndStudent_Id("sub-789", "student-123"))
                .thenReturn(Optional.of(submission));
        when(aiClient.generateStudentFeedback(anyString(), anyString()))
                .thenReturn("The error indicates a missing semicolon on line 3.");

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.COMPILER_ERROR, "Main.java:3: error: ';' expected");
        StudentAiResponse response = studentAiService.getSubmissionFeedback("sub-789", "student-123", request);

        assertNotNull(response);
        assertEquals(StudentAiMode.COMPILER_ERROR, response.mode());
        assertEquals("The error indicates a missing semicolon on line 3.", response.explanation());
        verify(dockerSandboxService, never()).execute(anyString());
    }

    @Test
    void testGetSubmissionFeedback_CompilerError_SandboxFallbackCompileSuccess() {
        when(submissionRepository.findByIdAndStudent_Id("sub-789", "student-123"))
                .thenReturn(Optional.of(submission));
        when(dockerSandboxService.execute(anyString()))
                .thenReturn(SandboxResult.builder().status(SandboxResult.Status.SUCCESS).build());

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.COMPILER_ERROR, null);
        StudentAiResponse response = studentAiService.getSubmissionFeedback("sub-789", "student-123", request);

        assertNotNull(response);
        assertEquals(StudentAiMode.COMPILER_ERROR, response.mode());
        assertTrue(response.explanation().contains("No compilation errors were detected"));
        verify(aiClient, never()).generateStudentFeedback(anyString(), anyString());
    }

    @Test
    void testGetSubmissionFeedback_DebugHints_Success() {
        when(submissionRepository.findByIdAndStudent_Id("sub-789", "student-123"))
                .thenReturn(Optional.of(submission));
        when(aiClient.generateStudentFeedback(anyString(), anyString()))
                .thenReturn("1. Check boundary conditions. 2. Verify array indices.");

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.DEBUG_HINTS, "Test failed on input 0");
        StudentAiResponse response = studentAiService.getSubmissionFeedback("sub-789", "student-123", request);

        assertNotNull(response);
        assertEquals(StudentAiMode.DEBUG_HINTS, response.mode());
        assertEquals("1. Check boundary conditions. 2. Verify array indices.", response.explanation());
    }

    @Test
    void testGetSubmissionFeedback_UnauthorizedMaskedAs404() {
        when(submissionRepository.findByIdAndStudent_Id("sub-789", "other-student"))
                .thenReturn(Optional.empty());

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.CODE_EXPLANATION, null);
        assertThrows(ResourceNotFoundException.class, () ->
                studentAiService.getSubmissionFeedback("sub-789", "other-student", request));
    }

    @Test
    void testGetSubmissionFeedback_ActiveExamRejectedWith403() {
        exam.setStatus("active");
        when(submissionRepository.findByIdAndStudent_Id("sub-789", "student-123"))
                .thenReturn(Optional.of(submission));

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.CODE_EXPLANATION, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                studentAiService.getSubmissionFeedback("sub-789", "student-123", request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("active examination"));
    }

    @Test
    void testRateLimit_ExceededThrows429() {
        String studentId = "rate-limit-student";
        when(submissionRepository.findByIdAndStudent_Id("sub-789", studentId))
                .thenReturn(Optional.of(submission));
        when(aiClient.generateStudentFeedback(anyString(), anyString()))
                .thenReturn("Explanation");

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.CODE_EXPLANATION, null);

        // First 10 requests succeed
        for (int i = 0; i < 10; i++) {
            studentAiService.getSubmissionFeedback("sub-789", studentId, request);
        }

        // 11th request triggers 429
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                studentAiService.getSubmissionFeedback("sub-789", studentId, request));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void testAiProviderError_PropagatesException() {
        when(submissionRepository.findByIdAndStudent_Id("sub-789", "student-123"))
                .thenReturn(Optional.of(submission));
        when(aiClient.generateStudentFeedback(anyString(), anyString()))
                .thenThrow(new AiIntegrationException("AI provider quota exceeded"));

        StudentAiRequest request = new StudentAiRequest(StudentAiMode.CODE_EXPLANATION, null);
        assertThrows(AiIntegrationException.class, () ->
                studentAiService.getSubmissionFeedback("sub-789", "student-123", request));
    }

    @Test
    void testGetSandboxFeedback_Success() {
        when(aiClient.generateStudentFeedback(anyString(), anyString()))
                .thenReturn("Sandbox code explanation");

        StudentSandboxAiRequest request = new StudentSandboxAiRequest(
                StudentAiMode.CODE_EXPLANATION,
                "class Solution {}",
                null
        );

        StudentAiResponse response = studentAiService.getSandboxFeedback("student-sandbox-user", request);
        assertNotNull(response);
        assertEquals("Sandbox code explanation", response.explanation());
    }
}
