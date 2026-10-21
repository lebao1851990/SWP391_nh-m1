package com.codeassess.scoring.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.scoring.dto.response.CriterionScoreDto;
import com.codeassess.scoring.dto.response.StudentSubmissionResult;
import com.codeassess.scoring.entity.RuleScore;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import java.util.stream.Collectors;

@Service
public class StudentScoringService {

    private final SubmissionRepository submissionRepository;
    private final RuleScoreRepository ruleScoreRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;

    public StudentScoringService(
            SubmissionRepository submissionRepository,
            RuleScoreRepository ruleScoreRepository,
            GradeAuditLogRepository gradeAuditLogRepository) {
        this.submissionRepository = submissionRepository;
        this.ruleScoreRepository = ruleScoreRepository;
        this.gradeAuditLogRepository = gradeAuditLogRepository;
    }

    @Transactional(readOnly = true)
    public StudentSubmissionResult getStudentResult(String submissionId, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(submissionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        List<RuleScore> ruleScores = ruleScoreRepository.findBySubmission_Id(submissionId);

        List<CriterionScoreDto> criteriaDtos = ruleScores.stream().map(rs -> CriterionScoreDto.builder()
                .criterionId(rs.getCriteria().getId())
                .name(rs.getCriteria().getName())
                .description(rs.getCriteria().getDescription())
                .score(rs.getScore())
                .maxScore(rs.getCriteria().getMaxScore())
                .build()).collect(Collectors.toList());

        BigDecimal effectiveScore = submission.getOverrideScore() != null ? submission.getOverrideScore() : submission.getTotalScore();

        BigDecimal maxTotalScore = criteriaDtos.stream()
                .map(CriterionScoreDto::getMaxScore)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (maxTotalScore.compareTo(BigDecimal.ZERO) == 0) {
            maxTotalScore = BigDecimal.valueOf(100);
        }

        boolean isDone = "COMPLETED".equalsIgnoreCase(submission.getStatus())
                || "EVALUATED".equalsIgnoreCase(submission.getStatus())
                || "GRADED".equalsIgnoreCase(submission.getStatus());

        String feedback = null;
        if (isDone) {
            List<GradeAuditLog> auditLogs = gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc(submissionId);
            for (GradeAuditLog log : auditLogs) {
                if (log.getRationale() != null && !log.getRationale().trim().isEmpty()) {
                    feedback = extractStudentFeedback(log.getRationale());
                    if (feedback != null) {
                        break;
                    }
                }
            }
        }

        return StudentSubmissionResult.builder()
                .submissionId(submission.getId())
                .examName(submission.getExam().getTitle())
                .status(submission.getStatus())
                .evaluatedAt(isDone ? submission.getEvaluatedAt() : null)
                .totalScore(submission.getTotalScore())
                .overrideTotalScore(submission.getOverrideScore())
                .effectiveScore(effectiveScore)
                .maxTotalScore(maxTotalScore)
                .criteria(criteriaDtos)
                .instructorFeedback(feedback)
                .instructorEvaluationComments(feedback)
                .build();
    }

    String extractStudentFeedback(String rationale) {
        if (rationale == null || rationale.trim().isEmpty()) {
            return null;
        }
        String trimmed = rationale.trim().replace("\r\n", "\n");

        // Case 1: Formatted with "Override Rationale:" header
        if (trimmed.startsWith("Override Rationale:")) {
            int commentIdx = trimmed.lastIndexOf("\nInstructor Comments:");
            if (commentIdx != -1) {
                String comments = trimmed.substring(commentIdx + "\nInstructor Comments:".length()).trim();
                return comments.isEmpty() ? null : comments;
            }
            // Standalone override rationale - internal teacher note, must NOT be exposed to student
            return null;
        }

        // Case 2: Formatted with "Instructor Comments:" header (comments only)
        if (trimmed.startsWith("Instructor Comments:")) {
            String comments = trimmed.substring("Instructor Comments:".length()).trim();
            return comments.isEmpty() ? null : comments;
        }

        // Case 3: Legacy/unidentified text without explicit public comment header
        // Under security policy, unidentified text must never be treated as public student feedback.
        return null;
    }
}
