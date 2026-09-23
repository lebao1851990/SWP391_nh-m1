package com.codeassess.scoring.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.dto.response.CriterionScoreDto;
import com.codeassess.scoring.dto.response.ScoringDetail;
import com.codeassess.scoring.entity.RuleScore;
import com.codeassess.scoring.entity.ScoringCriteria;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.scoring.repository.ScoringCriteriaRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherScoringService {

    private final SubmissionRepository submissionRepository;
    private final RuleScoreRepository ruleScoreRepository;
    private final ScoringCriteriaRepository scoringCriteriaRepository;

    @Transactional(readOnly = true)
    public ScoringDetail getScoringDetail(String submissionId, String teacherId) {
        Submission submission = submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(submissionId, teacherId)
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

        return ScoringDetail.builder()
                .submissionId(submission.getId())
                .studentId(submission.getStudent().getId())
                .studentName(submission.getStudent().getFullName())
                .examName(submission.getExam().getTitle())
                .className(submission.getExam().getCourseClass().getName())
                .evaluatedAt(submission.getEvaluatedAt())
                .status(submission.getStatus())
                .totalScore(submission.getTotalScore())
                .overrideTotalScore(submission.getOverrideScore())
                .effectiveScore(effectiveScore)
                .criteria(criteriaDtos)
                .build();
    }

    @Transactional
    public ScoringDetail updateScoring(String submissionId, String teacherId, SaveScoringPayload payload) {
        Submission submission = submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        ruleScoreRepository.deleteBySubmission_Id(submissionId);

        BigDecimal totalScore = BigDecimal.ZERO;

        if (payload.getCriteriaScores() != null) {
            for (SaveScoringPayload.CriteriaScoreRequest csRequest : payload.getCriteriaScores()) {
                ScoringCriteria criteria = scoringCriteriaRepository.findById(csRequest.getCriterionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Scoring criteria not found: " + csRequest.getCriterionId()));

                BigDecimal score = csRequest.getScore();
                if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(criteria.getMaxScore()) > 0) {
                    throw new IllegalArgumentException("Invalid score for criteria " + criteria.getId() + ": must be between 0 and " + criteria.getMaxScore());
                }

                RuleScore ruleScore = RuleScore.builder()
                        .submission(submission)
                        .criteria(criteria)
                        .score(score)
                        .build();

                ruleScoreRepository.save(ruleScore);
                totalScore = totalScore.add(score);
            }
        }

        submission.setTotalScore(totalScore);
        submission.setOverrideScore(payload.getOverrideTotalScore());
        submission.setEvaluatedAt(LocalDateTime.now());
        
        if (submission.getStatus() == null || submission.getStatus().equals("PENDING")) {
            submission.setStatus("GRADED");
        }

        submissionRepository.save(submission);

        return getScoringDetail(submissionId, teacherId);
    }
}
