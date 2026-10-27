package com.codeassess.testcase;

import com.codeassess.exam.entity.Exam;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchSuiteResult;
import com.codeassess.testcase.dto.sandbox.TestCaseExecutionItemResult;
import com.codeassess.testcase.entity.SubmissionTestResult;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.testcase.repository.TestCaseRepository;
import com.codeassess.testcase.service.AutomatedTestRunnerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutomatedTestRunnerService Unit Tests")
class AutomatedTestRunnerServiceTest {

    @Mock
    private DockerSandboxService dockerSandboxService;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    private AutomatedTestRunnerService runnerService;

    private Exam exam;
    private Submission submission;

    @BeforeEach
    void setUp() {
        runnerService = new AutomatedTestRunnerService(
                dockerSandboxService,
                testCaseRepository,
                submissionTestResultRepository,
                submissionRepository
        );

        exam = new Exam();
        exam.setId("exam-1");
        exam.setTitle("Java Final Exam");

        submission = new Submission();
        submission.setId("sub-100");
        submission.setExam(exam);
        submission.setSourceCode("public class Main { public static void main(String[] args) {} }");
        submission.setExecutionStatus("PENDING");
    }

    @Test
    @DisplayName("When exam has no test cases, submission is marked COMPLETED with null auto_score")
    void testExamWithNoTestCases() {
        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());
        when(dockerSandboxService.execute(submission.getSourceCode()))
                .thenReturn(com.codeassess.sandbox.dto.response.SandboxResult.builder()
                        .status(com.codeassess.sandbox.dto.response.SandboxResult.Status.SUCCESS)
                        .stdout("Run OK")
                        .compilerOutput("Compile OK")
                        .executionTimeMs(120L)
                        .build());

        runnerService.runAndGrade(submission);

        verify(dockerSandboxService, never()).executeTestCaseSuite(any(), any());
        assertEquals("COMPLETED", submission.getStatus());
        assertNull(submission.getAutoScore());
        assertEquals(0, submission.getTestCasesPassed());
        assertEquals(0, submission.getTestCasesTotal());
        verify(submissionRepository).save(submission);
    }

    @Test
    @DisplayName("When compilation fails, all test cases marked COMPILE_ERROR and score is 0")
    void testCompilationFailure() {
        TestCase tc1 = new TestCase();
        tc1.setId("tc-1");
        tc1.setName("Test 1");
        tc1.setPoints(BigDecimal.valueOf(10));
        tc1.setExpectedOutput("Output 1");

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc1));

        TestCaseBatchSuiteResult compileFailResult = TestCaseBatchSuiteResult.builder()
                .compiled(false)
                .compileOutput("Compilation error at line 1")
                .itemResults(Collections.emptyList())
                .build();
        when(dockerSandboxService.executeTestCaseSuite(eq(submission.getSourceCode()), anyList()))
                .thenReturn(compileFailResult);

        runnerService.runAndGrade(submission);

        assertEquals("COMPILE_ERROR", submission.getExecutionStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), submission.getAutoScore());
        assertEquals(0, submission.getTestCasesPassed());
        assertEquals(1, submission.getTestCasesTotal());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionTestResultRepository).saveAll(captor.capture());
        List<SubmissionTestResult> saved = captor.getValue();
        assertEquals(0, saved.size()); // Compile fail from suiteResult has empty itemResults unless generated
    }

    @Test
    @DisplayName("When all test cases pass with different weights, calculates 10.00 score and SUCCESS status")
    void testAllTestCasesPass() {
        TestCase tc1 = new TestCase();
        tc1.setId("tc-1");
        tc1.setName("Test 1");
        tc1.setPoints(BigDecimal.valueOf(30));
        tc1.setInputData("1 2");
        tc1.setExpectedOutput("3");

        TestCase tc2 = new TestCase();
        tc2.setId("tc-2");
        tc2.setName("Test 2");
        tc2.setPoints(BigDecimal.valueOf(70));
        tc2.setInputData("10 20");
        tc2.setExpectedOutput("30");

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc1, tc2));

        TestCaseExecutionItemResult res1 = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-1")
                .testCaseName("Test 1")
                .status("PASSED")
                .actualOutput("3\n")
                .expectedOutput("3")
                .pointsAwarded(BigDecimal.valueOf(30))
                .maxPoints(BigDecimal.valueOf(30))
                .exitCode(0)
                .executionTimeMs(50L)
                .build();

        TestCaseExecutionItemResult res2 = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-2")
                .testCaseName("Test 2")
                .status("PASSED")
                .actualOutput("30\r\n")
                .expectedOutput("30")
                .pointsAwarded(BigDecimal.valueOf(70))
                .maxPoints(BigDecimal.valueOf(70))
                .exitCode(0)
                .executionTimeMs(60L)
                .build();

        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .compileOutput("OK")
                .itemResults(List.of(res1, res2))
                .build();

        when(dockerSandboxService.executeTestCaseSuite(eq(submission.getSourceCode()), anyList()))
                .thenReturn(suiteResult);

        runnerService.runAndGrade(submission);

        assertEquals("SUCCESS", submission.getExecutionStatus());
        assertEquals(new BigDecimal("10.00"), submission.getAutoScore());
        assertEquals(new BigDecimal("10.00"), submission.getTotalScore());
        assertEquals(2, submission.getTestCasesPassed());
        assertEquals(2, submission.getTestCasesTotal());
    }

    @Test
    @DisplayName("Partial pass calculates proportional weighted auto_score on 10-point scale and PARTIAL_SUCCESS status")
    void testPartialPassWeightCalculation() {
        TestCase tc1 = new TestCase();
        tc1.setId("tc-1");
        tc1.setName("Easy Test");
        tc1.setPoints(BigDecimal.valueOf(20));
        tc1.setExpectedOutput("Pass");

        TestCase tc2 = new TestCase();
        tc2.setId("tc-2");
        tc2.setName("Hard Test");
        tc2.setPoints(BigDecimal.valueOf(80));
        tc2.setExpectedOutput("Complex Output");

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc1, tc2));

        TestCaseExecutionItemResult res1 = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-1")
                .testCaseName("Easy Test")
                .status("PASSED")
                .actualOutput("Pass\n")
                .expectedOutput("Pass")
                .pointsAwarded(BigDecimal.valueOf(20))
                .maxPoints(BigDecimal.valueOf(20))
                .exitCode(0)
                .build();

        TestCaseExecutionItemResult res2 = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-2")
                .testCaseName("Hard Test")
                .status("FAILED")
                .actualOutput("Wrong Output\n")
                .expectedOutput("Complex Output")
                .pointsAwarded(BigDecimal.ZERO)
                .maxPoints(BigDecimal.valueOf(80))
                .exitCode(0)
                .build();

        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .compileOutput("OK")
                .itemResults(List.of(res1, res2))
                .build();

        when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                .thenReturn(suiteResult);

        runnerService.runAndGrade(submission);

        assertEquals("PARTIAL_SUCCESS", submission.getExecutionStatus());
        // 20 out of 100 points -> 2.00 on 10-point scale
        assertEquals(new BigDecimal("2.00"), submission.getAutoScore());
        assertEquals(1, submission.getTestCasesPassed());
        assertEquals(2, submission.getTestCasesTotal());
    }

    @Test
    @DisplayName("Timeout test case gets TIMEOUT status and 0 points")
    void testTimeoutHandling() {
        TestCase tc = new TestCase();
        tc.setId("tc-1");
        tc.setName("Infinite Loop Test");
        tc.setPoints(BigDecimal.valueOf(10));
        tc.setExpectedOutput("Done");

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc));

        TestCaseExecutionItemResult res = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-1")
                .testCaseName("Infinite Loop Test")
                .status("TIMEOUT")
                .actualOutput("")
                .expectedOutput("Done")
                .exitCode(124)
                .pointsAwarded(BigDecimal.ZERO)
                .maxPoints(BigDecimal.valueOf(10))
                .executionTimeMs(5000L)
                .build();

        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .itemResults(List.of(res))
                .build();

        when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                .thenReturn(suiteResult);

        runnerService.runAndGrade(submission);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionTestResultRepository).saveAll(captor.capture());
        SubmissionTestResult saved = captor.getValue().get(0);
        assertEquals("TIMEOUT", saved.getStatus());
        assertEquals(BigDecimal.ZERO, saved.getPointsAwarded());
        assertEquals(0, submission.getTestCasesPassed());
    }
}
