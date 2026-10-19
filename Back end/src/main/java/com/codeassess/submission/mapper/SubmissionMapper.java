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

        return SubmissionDetailResponse.builder()
                .id(submission.getId())
                .examId(submission.getExam() != null ? submission.getExam().getId() : null)
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
                .build();
    }
}
