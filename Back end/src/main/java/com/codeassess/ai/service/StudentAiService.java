package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.StudentAiMode;
import com.codeassess.ai.dto.StudentAiRequest;
import com.codeassess.ai.dto.StudentAiResponse;
import com.codeassess.ai.dto.StudentSandboxAiRequest;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StudentAiService {

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_SOURCE_CHARS = 65_536;
    private static final int MAX_OUTPUT_CHARS = 8_192;

    private final SubmissionRepository submissionRepository;
    private final AiClient aiClient;
    private final DockerSandboxService dockerSandboxService;

    // In-memory sliding-window rate limiter keyed by student user ID
    private final ConcurrentMap<String, Deque<Long>> userRateLimits = new ConcurrentHashMap<>();

    public StudentAiService(SubmissionRepository submissionRepository, AiClient aiClient, DockerSandboxService dockerSandboxService) {
        this.submissionRepository = submissionRepository;
        this.aiClient = aiClient;
        this.dockerSandboxService = dockerSandboxService;
    }

    public StudentAiResponse getSubmissionFeedback(String submissionId, String studentId, StudentAiRequest request) {
        checkRateLimit(studentId);

        Submission submission = submissionRepository.findByIdAndStudent_Id(submissionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        // Enforce active exam rule: feedback is only allowed after exam has concluded
        if (submission.getExam() != null && "active".equalsIgnoreCase(submission.getExam().getStatus())) {
            log.warn("Student {} attempted to access AI assistant during active exam {}", studentId, submission.getExam().getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "AI Learning Assistant is not permitted during an active examination. Please wait until the assessment has concluded.");
        }

        String sourceCode = submission.getSourceCode();
        if (sourceCode == null || sourceCode.isBlank()) {
            return new StudentAiResponse(request.mode(), "No source code available for this submission.");
        }

        return generateFeedback(request.mode(), sourceCode, request.compilerOutput());
    }

    public StudentAiResponse getSandboxFeedback(String studentId, StudentSandboxAiRequest request) {
        checkRateLimit(studentId);
        return generateFeedback(request.mode(), request.sourceCode(), request.compilerOutput());
    }

    private StudentAiResponse generateFeedback(StudentAiMode mode, String rawSourceCode, String rawCompilerOutput) {
        String sourceCode = truncate(rawSourceCode, MAX_SOURCE_CHARS);
        String compilerOutput = truncate(rawCompilerOutput, MAX_OUTPUT_CHARS);

        if (mode == StudentAiMode.COMPILER_ERROR) {
            // If compiler output was not supplied, attempt to compile via DockerSandboxService
            if (compilerOutput == null || compilerOutput.isBlank()) {
                try {
                    SandboxResult sandboxResult = dockerSandboxService.execute(sourceCode);
                    if (sandboxResult.getStatus() == SandboxResult.Status.SUCCESS) {
                        return new StudentAiResponse(StudentAiMode.COMPILER_ERROR,
                                "No compilation errors were detected. The source code compiled successfully in the sandbox environment.");
                    } else {
                        String stderr = sandboxResult.getStderr();
                        String stdout = sandboxResult.getStdout();
                        compilerOutput = (stderr != null && !stderr.isBlank()) ? stderr : stdout;
                    }
                } catch (SandboxException ex) {
                    log.warn("Sandbox execution unavailable for compiler error analysis: {}", ex.getMessage());
                    compilerOutput = "Compiler output unavailable from sandbox: " + ex.getMessage();
                } catch (Exception ex) {
                    log.warn("Unexpected error running sandbox for student compiler check: {}", ex.getMessage());
                }
            }

            if (compilerOutput == null || compilerOutput.isBlank()) {
                return new StudentAiResponse(StudentAiMode.COMPILER_ERROR,
                        "No compiler error output was provided or detected to explain.");
            }

            String systemPrompt = """
                    You are CodeAssess Learning Assistant, a friendly and pedagogical programming tutor.
                    Your goal is to explain compilation or build errors to students in clear, conceptual language.
                    
                    Guidelines:
                    - Explain what the error message means in plain, student-friendly terms.
                    - Identify the probable typographical, syntax, or type mistake in the student's code.
                    - Provide incremental guidance on how the student can fix it themselves.
                    - DO NOT rewrite the student's full solution or provide completed replacement code.
                    - Maintain an encouraging and constructive instructional tone.
                    - Explicitly state that your advice is advisory and for learning purposes.
                    """;

            String userPrompt = "Student Source Code:\n```java\n" + sourceCode + "\n```\n\n"
                    + "Compiler / Build Output:\n```\n" + compilerOutput + "\n```\n\n"
                    + "Please explain this compiler error conceptually and guide the student on how to resolve it.";

            String explanation = aiClient.generateStudentFeedback(systemPrompt, userPrompt);
            return new StudentAiResponse(StudentAiMode.COMPILER_ERROR, explanation);

        } else if (mode == StudentAiMode.CODE_EXPLANATION) {
            String systemPrompt = """
                    You are CodeAssess Learning Assistant, an educational programming tutor.
                    Your goal is to explain what the student's submitted code does step-by-step.
                    
                    Guidelines:
                    - Break down the structure, methods, logic flow, and key algorithms in an educational tone.
                    - Highlight good programming practices used and areas where clarity could be improved.
                    - Discuss time and space complexity conceptually if applicable.
                    - DO NOT rewrite the code or provide a new solution.
                    - This explanation is strictly educational and advisory.
                    """;

            String userPrompt = "Student Source Code:\n```java\n" + sourceCode + "\n```\n\n"
                    + "Please explain the structure, execution flow, and behavior of this code.";

            String explanation = aiClient.generateStudentFeedback(systemPrompt, userPrompt);
            return new StudentAiResponse(StudentAiMode.CODE_EXPLANATION, explanation);

        } else if (mode == StudentAiMode.DEBUG_HINTS) {
            String systemPrompt = """
                    You are CodeAssess Learning Assistant, an interactive Socratic debugging coach.
                    Your goal is to give incremental debugging hints to help the student find and fix bugs on their own.
                    
                    Guidelines:
                    - Give 2-3 progressive, targeted clues or guiding questions (e.g., edge cases, boundary conditions, null checks, loop conditions).
                    - DO NOT give away the complete bug fix or write out the corrected code.
                    - Encourage critical thinking and diagnostic reasoning.
                    - Remind the student that debugging guidance is advisory.
                    """;

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Student Source Code:\n```java\n").append(sourceCode).append("\n```\n\n");
            if (compilerOutput != null && !compilerOutput.isBlank()) {
                userPrompt.append("Test / Runtime / Error Output:\n```\n").append(compilerOutput).append("\n```\n\n");
            }
            userPrompt.append("Please provide 2-3 incremental debugging clues without giving away the complete solution.");

            String explanation = aiClient.generateStudentFeedback(systemPrompt, userPrompt.toString());
            return new StudentAiResponse(StudentAiMode.DEBUG_HINTS, explanation);
        }

        throw new IllegalArgumentException("Unsupported Student AI mode: " + mode);
    }

    private void checkRateLimit(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        Deque<Long> timestamps = userRateLimits.computeIfAbsent(studentId, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst() > WINDOW_MILLIS)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for student {}", studentId);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "AI assistance rate limit exceeded (maximum " + MAX_REQUESTS_PER_MINUTE + " requests per minute). Please wait a moment before trying again.");
            }

            timestamps.addLast(now);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
