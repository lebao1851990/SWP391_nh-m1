package com.codeassess.scoring.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.scoring.dto.response.ExamScoreAggregationDto;
import com.codeassess.scoring.dto.response.QuestionScoreDetailDto;
import com.codeassess.scoring.entity.StudentQuestionScore;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExamScoreAggregationService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final StudentQuestionScoreRepository studentQuestionScoreRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final QuestionScoreOverrideService questionScoreOverrideService;

    public ExamScoreAggregationService(
            ExamRepository examRepository,
            ExamQuestionRepository examQuestionRepository,
            StudentQuestionScoreRepository studentQuestionScoreRepository,
            SubmissionRepository submissionRepository,
            UserRepository userRepository) {
        this(examRepository, examQuestionRepository, studentQuestionScoreRepository,
                submissionRepository, userRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExamScoreAggregationService(
            ExamRepository examRepository,
            ExamQuestionRepository examQuestionRepository,
            StudentQuestionScoreRepository studentQuestionScoreRepository,
            SubmissionRepository submissionRepository,
            UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) QuestionScoreOverrideService questionScoreOverrideService) {
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.studentQuestionScoreRepository = studentQuestionScoreRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.questionScoreOverrideService = questionScoreOverrideService;
    }

    @Transactional(readOnly = true)
    public ExamScoreAggregationDto calculateExamScore(String examId, String studentId) {
        return calculateExamScore(examId, studentId, null);
    }

    @Transactional(readOnly = true)
    public ExamScoreAggregationDto calculateExamScore(String examId, String studentId, String teacherId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));

        if (teacherId != null && questionScoreOverrideService != null) {
            questionScoreOverrideService.verifyTeacherAccess(exam, teacherId);
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found or access denied"));

        List<ExamQuestion> questions = examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId);
        List<StudentQuestionScore> scoreRecords = studentQuestionScoreRepository.findByExam_IdAndStudent_Id(examId, studentId);

        Map<String, StudentQuestionScore> scoreMap = scoreRecords.stream()
                .filter(sqs -> sqs.getQuestion() != null)
                .collect(Collectors.toMap(sqs -> sqs.getQuestion().getId(), sqs -> sqs, (a, b) -> a));

        // Case 1: Multi-Question Exam
        if (!questions.isEmpty()) {
            List<QuestionScoreDetailDto> detailDtos = new ArrayList<>();
            BigDecimal sumWeightedScore = BigDecimal.ZERO;
            BigDecimal sumWeights = BigDecimal.ZERO;
            BigDecimal sumPointsEarned = BigDecimal.ZERO;

            boolean hasPending = false;
            int completedCount = 0;
            int pendingCount = 0;
            int submittedCount = 0;

            for (ExamQuestion q : questions) {
                StudentQuestionScore sqs = scoreMap.get(q.getId());
                BigDecimal maxScore = q.getMaxScore() != null ? q.getMaxScore() : BigDecimal.valueOf(10.00);
                sumWeights = sumWeights.add(maxScore);

                final BigDecimal autoScore;
                final BigDecimal overrideScore;
                final BigDecimal effectiveScore;
                final String status;
                final boolean isOverridden;
                final String latestSubId;
                final Integer subCount;
                final java.time.LocalDateTime lastSubmittedAt;
                final String overrideReason;
                final String overriddenBy;
                final java.time.LocalDateTime overriddenAt;

                if (sqs != null) {
                    submittedCount++;
                    autoScore = sqs.getAutoScore();
                    overrideScore = sqs.getOverrideScore();
                    effectiveScore = sqs.getEffectiveScore() != null ? sqs.getEffectiveScore() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    status = sqs.getLatestStatus() != null ? sqs.getLatestStatus() : "PENDING";
                    isOverridden = overrideScore != null;
                    latestSubId = sqs.getLatestSubmissionId();
                    subCount = sqs.getSubmissionCount();
                    lastSubmittedAt = sqs.getLastSubmittedAt();
                    overrideReason = sqs.getOverrideReason();
                    overriddenBy = sqs.getOverriddenBy();
                    overriddenAt = sqs.getOverriddenAt();

                    if ("COMPLETED".equalsIgnoreCase(status) || "GRADED".equalsIgnoreCase(status) || "EVALUATED".equalsIgnoreCase(status)) {
                        completedCount++;
                    } else if ("PENDING".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status)) {
                        hasPending = true;
                        pendingCount++;
                    }
                } else {
                    autoScore = null;
                    overrideScore = null;
                    effectiveScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    status = "NOT_SUBMITTED";
                    isOverridden = false;
                    latestSubId = null;
                    subCount = 0;
                    lastSubmittedAt = null;
                    overrideReason = null;
                    overriddenBy = null;
                    overriddenAt = null;
                }

                // Weighted score: effective_score * max_score
                sumWeightedScore = sumWeightedScore.add(effectiveScore.multiply(maxScore));
                // Earned points: (effective_score / 10.0) * max_score
                BigDecimal points = effectiveScore.multiply(maxScore).divide(BigDecimal.valueOf(10.0), 2, RoundingMode.HALF_UP);
                sumPointsEarned = sumPointsEarned.add(points);

                detailDtos.add(QuestionScoreDetailDto.builder()
                        .questionId(q.getId())
                        .questionTitle(q.getTitle())
                        .displayOrder(q.getDisplayOrder())
                        .maxScore(maxScore)
                        .autoScore(autoScore)
                        .overrideScore(overrideScore)
                        .effectiveScore(effectiveScore)
                        .overrideReason(overrideReason)
                        .overriddenBy(overriddenBy)
                        .overriddenAt(overriddenAt)
                        .status(status)
                        .isOverridden(isOverridden)
                        .latestSubmissionId(latestSubId)
                        .submissionCount(subCount)
                        .lastSubmittedAt(lastSubmittedAt)
                        .build());
            }

            BigDecimal examScore10 = sumWeights.compareTo(BigDecimal.ZERO) > 0
                    ? sumWeightedScore.divide(sumWeights, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            return ExamScoreAggregationDto.builder()
                    .examId(examId)
                    .examTitle(exam.getTitle())
                    .studentId(studentId)
                    .studentName(student.getFullName())
                    .examScore10(examScore10)
                    .totalPointsEarned(sumPointsEarned.setScale(2, RoundingMode.HALF_UP))
                    .totalMaxPoints(sumWeights.setScale(2, RoundingMode.HALF_UP))
                    .status(hasPending ? "PROVISIONAL" : "FINAL")
                    .isProvisional(hasPending)
                    .totalQuestions(questions.size())
                    .submittedQuestions(submittedCount)
                    .completedQuestions(completedCount)
                    .pendingQuestions(pendingCount)
                    .questionScores(detailDtos)
                    .build();
        }

        // Case 2: Legacy Single-Question Exam (fallback without ExamQuestions)
        Optional<Submission> latestSubOpt = submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc(examId, studentId);
        BigDecimal effective = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String status = "NOT_SUBMITTED";
        boolean hasPending = false;
        BigDecimal autoScore = null;
        BigDecimal overrideScore = null;
        String latestSubId = null;

        if (latestSubOpt.isPresent()) {
            Submission sub = latestSubOpt.get();
            latestSubId = sub.getId();
            status = sub.getStatus();
            autoScore = sub.getAutoScore();
            overrideScore = sub.getOverrideScore();
            effective = overrideScore != null ? overrideScore : (sub.getTotalScore() != null ? sub.getTotalScore() : (autoScore != null ? autoScore : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            if ("PENDING".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status)) {
                hasPending = true;
            }
        }

        return ExamScoreAggregationDto.builder()
                .examId(examId)
                .examTitle(exam.getTitle())
                .studentId(studentId)
                .studentName(student.getFullName())
                .examScore10(effective)
                .totalPointsEarned(effective)
                .totalMaxPoints(BigDecimal.valueOf(10.00).setScale(2, RoundingMode.HALF_UP))
                .status(hasPending ? "PROVISIONAL" : "FINAL")
                .isProvisional(hasPending)
                .totalQuestions(1)
                .submittedQuestions(latestSubOpt.isPresent() ? 1 : 0)
                .completedQuestions("COMPLETED".equalsIgnoreCase(status) ? 1 : 0)
                .pendingQuestions(hasPending ? 1 : 0)
                .questionScores(List.of(QuestionScoreDetailDto.builder()
                        .questionId("legacy")
                        .questionTitle(exam.getTitle())
                        .displayOrder(1)
                        .maxScore(BigDecimal.valueOf(10.00))
                        .autoScore(autoScore)
                        .overrideScore(overrideScore)
                        .effectiveScore(effective)
                        .status(status)
                        .isOverridden(overrideScore != null)
                        .latestSubmissionId(latestSubId)
                        .submissionCount(latestSubOpt.isPresent() ? 1 : 0)
                        .build()))
                .build();
    }
}

