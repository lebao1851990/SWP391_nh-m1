package com.codeassess.scoring.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.dto.response.CriterionScoreDto;
import com.codeassess.scoring.dto.response.GradeAuditLogDto;
import com.codeassess.scoring.dto.response.ScoringDetail;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.entity.RuleScore;
import com.codeassess.scoring.entity.ScoringCriteria;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.scoring.repository.ScoringCriteriaRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeacherScoringService {

    private final SubmissionRepository submissionRepository;
    private final RuleScoreRepository ruleScoreRepository;
    private final ScoringCriteriaRepository scoringCriteriaRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;
    private final UserRepository userRepository;

    public TeacherScoringService(
            SubmissionRepository submissionRepository,
            RuleScoreRepository ruleScoreRepository,
            ScoringCriteriaRepository scoringCriteriaRepository,
            GradeAuditLogRepository gradeAuditLogRepository,
            UserRepository userRepository) {
        this.submissionRepository = submissionRepository;
        this.ruleScoreRepository = ruleScoreRepository;
        this.scoringCriteriaRepository = scoringCriteriaRepository;
        this.gradeAuditLogRepository = gradeAuditLogRepository;
        this.userRepository = userRepository;
    }

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

        BigDecimal maxTotalScore = criteriaDtos.stream()
                .map(CriterionScoreDto::getMaxScore)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (maxTotalScore.compareTo(BigDecimal.ZERO) == 0) {
            maxTotalScore = BigDecimal.valueOf(100);
        }

        boolean isDone = "COMPLETED".equalsIgnoreCase(submission.getStatus())
                || "EVALUATED".equalsIgnoreCase(submission.getStatus());

        return ScoringDetail.builder()
                .submissionId(submission.getId())
                .studentId(submission.getStudent().getId())
                .studentName(submission.getStudent().getFullName())
                .examName(submission.getExam().getTitle())
                .className(submission.getExam().getCourseClass().getName())
                .evaluatedAt(isDone ? submission.getEvaluatedAt() : null)
                .status(submission.getStatus())
                .totalScore(submission.getTotalScore())
                .overrideTotalScore(submission.getOverrideScore())
                .effectiveScore(effectiveScore)
                .maxTotalScore(maxTotalScore)
                .criteria(criteriaDtos)
                .build();
    }

    @Transactional
    public ScoringDetail updateScoring(String submissionId, String teacherId, SaveScoringPayload payload) {
        Submission submission = submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        User actor = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher user not found: " + teacherId));

        BigDecimal preRubricScore = submission.getTotalScore();
        BigDecimal preOverrideScore = submission.getOverrideScore();
        BigDecimal preEffectiveScore = preOverrideScore != null ? preOverrideScore : preRubricScore;
        boolean isInitialGrading = submission.getEvaluatedAt() == null 
                || submission.getStatus() == null 
                || "PENDING".equalsIgnoreCase(submission.getStatus());

        List<RuleScore> existingRuleScores = ruleScoreRepository.findBySubmission_Id(submissionId);
        Map<String, BigDecimal> preCriteriaMap = existingRuleScores.stream()
                .filter(rs -> rs.getCriteria() != null)
                .collect(Collectors.toMap(rs -> rs.getCriteria().getId(), RuleScore::getScore, (a, b) -> a));

        ruleScoreRepository.deleteBySubmission_Id(submissionId);

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxScale = BigDecimal.ZERO;
        Map<String, BigDecimal> postCriteriaMap = new java.util.HashMap<>();

        if (payload.getCriteriaScores() != null) {
            for (SaveScoringPayload.CriteriaScoreRequest csRequest : payload.getCriteriaScores()) {
                ScoringCriteria criteria = scoringCriteriaRepository.findById(csRequest.getCriterionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Scoring criteria not found: " + csRequest.getCriterionId()));

                if (criteria.getMaxScore() != null) {
                    maxScale = maxScale.add(criteria.getMaxScore());
                }

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
                postCriteriaMap.put(csRequest.getCriterionId(), score);
            }
        }

        if (maxScale.compareTo(BigDecimal.ZERO) == 0) {
            List<ScoringCriteria> allCriteria = scoringCriteriaRepository.findAll();
            for (ScoringCriteria sc : allCriteria) {
                if (sc.getMaxScore() != null) {
                    maxScale = maxScale.add(sc.getMaxScore());
                }
            }
        }
        if (maxScale.compareTo(BigDecimal.ZERO) == 0) {
            maxScale = BigDecimal.valueOf(100);
        }

        if (payload.getOverrideTotalScore() != null) {
            if (payload.getOverrideTotalScore().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Override score cannot be negative");
            }
            if (payload.getOverrideTotalScore().compareTo(maxScale) > 0) {
                throw new IllegalArgumentException("Override score cannot exceed maximum assessment score of " + maxScale);
            }
        }

        BigDecimal postRubricScore = totalScore;
        BigDecimal postOverrideScore = payload.getOverrideTotalScore();
        BigDecimal postEffectiveScore = postOverrideScore != null ? postOverrideScore : postRubricScore;

        boolean isOverrideChanged = (preOverrideScore == null && postOverrideScore != null)
                || (preOverrideScore != null && postOverrideScore == null)
                || (preOverrideScore != null && postOverrideScore != null && preOverrideScore.compareTo(postOverrideScore) != 0);

        boolean isRubricChanged = (preRubricScore == null && postRubricScore != null)
                || (preRubricScore != null && postRubricScore == null)
                || (preRubricScore != null && postRubricScore != null && preRubricScore.compareTo(postRubricScore) != 0)
                || !preCriteriaMap.equals(postCriteriaMap);

        String actionType = null;
        if (isInitialGrading) {
            actionType = "INITIAL_GRADING";
        } else if (preOverrideScore == null && postOverrideScore != null) {
            actionType = "OVERRIDE_SET";
        } else if (preOverrideScore != null && postOverrideScore == null) {
            actionType = "OVERRIDE_CLEARED";
        } else if (preOverrideScore != null && postOverrideScore != null && preOverrideScore.compareTo(postOverrideScore) != 0) {
            actionType = "OVERRIDE_UPDATE";
        } else if (isRubricChanged) {
            actionType = "RUBRIC_UPDATE";
        }

        submission.setTotalScore(totalScore);
        submission.setOverrideScore(postOverrideScore);
        submission.setEvaluatedAt(LocalDateTime.now());
        
        if (submission.getStatus() == null || submission.getStatus().equals("PENDING")) {
            submission.setStatus("GRADED");
        }

        submissionRepository.save(submission);

        if (actionType != null) {
            GradeAuditLog auditLog = GradeAuditLog.builder()
                    .submission(submission)
                    .actor(actor)
                    .actionType(actionType)
                    .preRubricScore(preRubricScore)
                    .postRubricScore(postRubricScore)
                    .preOverrideScore(preOverrideScore)
                    .postOverrideScore(postOverrideScore)
                    .preEffectiveScore(preEffectiveScore)
                    .postEffectiveScore(postEffectiveScore)
                    .rationale(buildConsolidatedRationale(payload.getOverrideRationale(), payload.getInstructorEvaluationComments()))
                    .build();

            gradeAuditLogRepository.save(auditLog);
        }

        return getScoringDetail(submissionId, teacherId);
    }

    private String buildConsolidatedRationale(String overrideRationale, String instructorComments) {
        boolean hasRationale = overrideRationale != null && !overrideRationale.trim().isEmpty();
        boolean hasComments = instructorComments != null && !instructorComments.trim().isEmpty();

        if (hasRationale && hasComments) {
            return "Override Rationale: " + overrideRationale.trim() + "\nInstructor Comments: " + instructorComments.trim();
        } else if (hasRationale) {
            return overrideRationale.trim();
        } else if (hasComments) {
            return instructorComments.trim();
        } else {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<GradeAuditLogDto> getAuditLogs(String submissionId, String teacherId) {
        Submission submission = submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        return gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc(submissionId).stream()
                .map(log -> GradeAuditLogDto.builder()
                        .id(log.getId())
                        .submissionId(submission.getId())
                        .actorId(log.getActor() != null ? log.getActor().getId() : null)
                        .actorName(log.getActor() != null ? log.getActor().getFullName() : null)
                        .actorEmail(log.getActor() != null ? log.getActor().getEmail() : null)
                        .actionType(log.getActionType())
                        .preRubricScore(log.getPreRubricScore())
                        .postRubricScore(log.getPostRubricScore())
                        .preOverrideScore(log.getPreOverrideScore())
                        .postOverrideScore(log.getPostOverrideScore())
                        .preEffectiveScore(log.getPreEffectiveScore())
                        .postEffectiveScore(log.getPostEffectiveScore())
                        .rationale(log.getRationale())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
