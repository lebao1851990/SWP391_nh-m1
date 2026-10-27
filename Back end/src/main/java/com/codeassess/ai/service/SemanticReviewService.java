package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.CodeReviewIssueDto;
import com.codeassess.ai.dto.SemanticReviewResponse;
import com.codeassess.ai.prompt.AiPromptTemplates;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SemanticReviewService {

    private final SubmissionRepository submissionRepository;
    private final AiClient aiClient;
    private final DockerSandboxService dockerSandboxService;
    private final ObjectMapper objectMapper;

    private static final String SEMANTIC_REVIEW_PREFIX = "<!--SEMANTIC_REVIEW_JSON-->";

    /**
     * Performs a semantic code review for a student submission.
     * Enforces active exam lock to prevent illicit academic assistance during live assessments.
     */
    public SemanticReviewResponse reviewForStudent(String submissionId, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(submissionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        if (submission.getExam() != null && "active".equalsIgnoreCase(submission.getExam().getStatus())) {
            log.warn("Student {} attempted to trigger Semantic Review during active exam {}", studentId, submission.getExam().getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Semantic Code Review is not permitted during an active examination. Please wait until the assessment has concluded.");
        }

        return getOrCreateReview(submission);
    }

    /**
     * Performs a semantic code review for an instructor viewing a submission.
     */
    public SemanticReviewResponse reviewForTeacher(String submissionId, String teacherId) {
        Submission submission = submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        return getOrCreateReview(submission);
    }

    private SemanticReviewResponse getOrCreateReview(Submission submission) {
        // 1. Check if an existing review is cached in submission.instructorFeedback
        if (submission.getInstructorFeedback() != null && submission.getInstructorFeedback().contains(SEMANTIC_REVIEW_PREFIX)) {
            try {
                String raw = submission.getInstructorFeedback();
                int idx = raw.indexOf(SEMANTIC_REVIEW_PREFIX) + SEMANTIC_REVIEW_PREFIX.length();
                String jsonPart = raw.substring(idx).trim();
                SemanticReviewResponse cached = objectMapper.readValue(jsonPart, SemanticReviewResponse.class);
                log.info("Returning cached SemanticReview for submission {}", submission.getId());
                return cached;
            } catch (Exception ex) {
                log.warn("Failed to deserialize cached semantic review for submission {}: {}", submission.getId(), ex.getMessage());
            }
        }

        // 2. Prepare Source Code
        String sourceCode = submission.getSourceCode();
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submission does not contain readable source code for review.");
        }

        // 3. Compile or retrieve real compiler output if needed
        String compilerOutput = submission.getCompileOutput();
        if (compilerOutput == null || compilerOutput.isBlank()) {
            try {
                SandboxResult sandboxResult = dockerSandboxService.execute(sourceCode);
                if (sandboxResult.getStatus() != SandboxResult.Status.SUCCESS) {
                    String stderr = sandboxResult.getStderr();
                    String stdout = sandboxResult.getStdout();
                    compilerOutput = (stderr != null && !stderr.isBlank()) ? stderr : stdout;
                }
            } catch (Exception ex) {
                log.debug("Sandbox execution skipped or not available for submission {}: {}", submission.getId(), ex.getMessage());
            }
        }

        // 4. Construct Bounded User Prompt
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Student Submission Source Code (Numbered lines):\n```java\n");
        String[] lines = sourceCode.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            userPrompt.append(String.format("%3d | %s\n", i + 1, lines[i]));
        }
        userPrompt.append("```\n\n");

        if (compilerOutput != null && !compilerOutput.isBlank()) {
            userPrompt.append("Actual Compiler / Runtime Output:\n```\n")
                    .append(compilerOutput)
                    .append("\n```\n\n");
        } else {
            userPrompt.append("Compiler status: Code compiled cleanly with zero build errors.\n\n");
        }

        userPrompt.append("Please evaluate this submission and return the structured JSON review.");

        // 5. Invoke AI Client with Structured Output
        SemanticReviewResponse response = aiClient.generateStructuredOutput(
                AiPromptTemplates.SEMANTIC_REVIEW_SYSTEM,
                userPrompt.toString(),
                SemanticReviewResponse.class
        );

        // 6. Validate & Normalize Output
        if (response.getCleanCodeScore() == null) {
            response.setCleanCodeScore(BigDecimal.valueOf(7.00));
        } else {
            BigDecimal score = response.getCleanCodeScore().setScale(2, RoundingMode.HALF_UP);
            if (score.compareTo(BigDecimal.ZERO) < 0) score = BigDecimal.ZERO;
            if (score.compareTo(BigDecimal.TEN) > 0) score = BigDecimal.TEN;
            response.setCleanCodeScore(score);
        }

        // Validate line bounds: line cannot exceed source code length
        int totalLines = lines.length;
        if (response.getIssues() != null) {
            for (CodeReviewIssueDto issue : response.getIssues()) {
                if (issue.getLine() != null && (issue.getLine() <= 0 || issue.getLine() > totalLines)) {
                    issue.setLine(null); // safely reset hallucinated line numbers
                }
            }
        }

        // 7. Persist cached review into instructorFeedback without breaking existing feedback
        try {
            String jsonPayload = objectMapper.writeValueAsString(response);
            String existingFeedback = submission.getInstructorFeedback();
            String prefixComment = (existingFeedback != null && !existingFeedback.isBlank())
                    ? existingFeedback.split("<!--SEMANTIC_REVIEW_JSON-->")[0].trim() + "\n\n"
                    : "";
            submission.setInstructorFeedback(prefixComment + SEMANTIC_REVIEW_PREFIX + "\n" + jsonPayload);
            submissionRepository.save(submission);
        } catch (Exception ex) {
            log.error("Failed to persist cached semantic review into submission {}: {}", submission.getId(), ex.getMessage());
        }

        return response;
    }
}
