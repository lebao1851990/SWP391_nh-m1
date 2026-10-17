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
import java.util.stream.Collectors;

@Service
public class StudentScoringService {

    private final SubmissionRepository submissionRepository;
    private final RuleScoreRepository ruleScoreRepository;

    public StudentScoringService(SubmissionRepository submissionRepository, RuleScoreRepository ruleScoreRepository) {
        this.submissionRepository = submissionRepository;
        this.ruleScoreRepository = ruleScoreRepository;
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
                || "EVALUATED".equalsIgnoreCase(submission.getStatus());

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
                .build();
    }
}
