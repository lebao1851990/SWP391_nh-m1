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

        return SubmissionListResponse.builder()
                .id(submission.getId())
                .examId(submission.getExam() != null ? submission.getExam().getId() : null)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : null)
                .status(submission.getStatus())
                .totalScore(submission.getTotalScore())
                .overrideScore(submission.getOverrideScore())
                .evaluatedAt(submission.getEvaluatedAt())
                .createdAt(submission.getCreatedAt())
                .build();
    }

    public SubmissionDetailResponse toDetailResponse(Submission submission) {
        if (submission == null) {
            return null;
        }

        return SubmissionDetailResponse.builder()
                .id(submission.getId())
                .examId(submission.getExam() != null ? submission.getExam().getId() : null)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : null)
                .status(submission.getStatus())
                .sourceCode(submission.getSourceCode())
                .totalScore(submission.getTotalScore())
                .overrideScore(submission.getOverrideScore())
                .evaluatedAt(submission.getEvaluatedAt())
                .createdAt(submission.getCreatedAt())
                .build();
    }
}
