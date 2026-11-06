package com.codeassess.submission.mapper;

import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import org.springframework.stereotype.Component;

@Component
public class SubmissionMapper {

    public SubmissionListResponse toListResponse(Submission submission) {
        if (submission == null) {
            return null;
        }

        boolean isDone = "COMPLETED".equalsIgnoreCase(submission.getStatus())
                || "EVALUATED".equalsIgnoreCase(submission.getStatus());

        return SubmissionListResponse.builder()
                .id(submission.getId())
                .examId(submission.getExam() != null ? submission.getExam().getId() : null)
                .examTitle(submission.getExam() != null ? submission.getExam().getTitle() : null)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : null)
                .status(submission.getStatus())
                .totalScore(submission.getTotalScore())
                .overrideScore(submission.getOverrideScore())
                .evaluatedAt(isDone ? submission.getEvaluatedAt() : null)
                .createdAt(submission.getCreatedAt())
                .build();
    }

    public SubmissionDetailResponse toDetailResponse(Submission submission) {
        if (submission == null) {
            return null;
        }

        boolean isDone = "COMPLETED".equalsIgnoreCase(submission.getStatus())
                || "EVALUATED".equalsIgnoreCase(submission.getStatus());

        String questionTitle = null;
        if (submission.getQuestion() != null) {
            try {
                questionTitle = submission.getQuestion().getTitle();
            } catch (Exception ignored) {
                // Ignore LazyInitializationException if Hibernate session is detached
            }
        }

        return SubmissionDetailResponse.builder()
                .id(submission.getId())
                .examId(submission.getExam() != null ? submission.getExam().getId() : null)
                .questionId(submission.getQuestion() != null ? submission.getQuestion().getId() : null)
                .questionTitle(questionTitle)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : null)
                .status(submission.getStatus())
                .sourceCode(submission.getSourceCode())
                .totalScore(submission.getTotalScore())
                .overrideScore(submission.getOverrideScore())
                .autoScore(submission.getAutoScore())
                .testCasesPassed(submission.getTestCasesPassed())
                .testCasesTotal(submission.getTestCasesTotal())
                .evaluatedAt(isDone ? submission.getEvaluatedAt() : null)
                .createdAt(submission.getCreatedAt())
                .submissionType(submission.getSubmissionType())
                .originalFilename(submission.getOriginalFilename())
                .archiveHash(submission.getArchiveHash())
                .sourceTreeHash(submission.getSourceTreeHash())
                .fileCount(submission.getFileCount())
                .entryPointClass(submission.getEntryPointClass())
                .executionStatus(submission.getExecutionStatus())
                .exitCode(submission.getExitCode())
                .compileOutput(submission.getCompileOutput())
                .stdout(submission.getStdout())
                .stderr(submission.getStderr())
                .executionDurationMs(submission.getExecutionDurationMs())
                .instructorFeedback(submission.getInstructorFeedback())
                .reviewStatus(submission.getReviewStatus())
                .publicationStatus(submission.getPublicationStatus())
                .publishedScore(submission.getPublishedScore())
                .build();
    }

    public SubmissionDetailResponse toStudentDetailResponse(Submission submission) {
        if (submission == null) {
            return null;
        }
        SubmissionDetailResponse dto = toDetailResponse(submission);
        // Mask provisional scores and comments if not published
        if (submission.getPublicationStatus() == null || !"PUBLISHED".equalsIgnoreCase(submission.getPublicationStatus())) {
            dto.setTotalScore(null);
            dto.setOverrideScore(null);
            dto.setAutoScore(null);
            dto.setPublishedScore(null);
            dto.setInstructorFeedback(null);
        }
        return dto;
    }

    public SubmissionListResponse toStudentListResponse(Submission submission) {
        if (submission == null) {
            return null;
        }
        SubmissionListResponse dto = toListResponse(submission);
        // Mask provisional scores if not published
        if (submission.getPublicationStatus() == null || !"PUBLISHED".equalsIgnoreCase(submission.getPublicationStatus())) {
            dto.setTotalScore(null);
            dto.setOverrideScore(null);
        }
        return dto;
    }
}
