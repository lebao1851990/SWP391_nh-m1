package com.codeassess.scoring.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.scoring.dto.request.QuestionOverrideRequestDto;
import com.codeassess.scoring.dto.response.QuestionScoreDetailDto;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.entity.StudentQuestionScore;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
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
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionScoreOverrideService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final StudentQuestionScoreRepository studentQuestionScoreRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;
    private final UserRepository userRepository;

    public void verifyTeacherAccess(Exam exam, String teacherId) {
        boolean hasAccess = (exam.getCourseClass() != null && exam.getCourseClass().getTeacher() != null
                && teacherId.equals(exam.getCourseClass().getTeacher().getId()))
                || (exam.getClasses() != null && exam.getClasses().stream().anyMatch(
                        c -> c.getTeacher() != null && teacherId.equals(c.getTeacher().getId())));

        if (!hasAccess) {
            throw new ResourceNotFoundException("Exam not found or access denied");
        }
    }

    @Transactional
    public QuestionScoreDetailDto setQuestionOverride(
            String examId,
            String questionId,
            String studentId,
            QuestionOverrideRequestDto request,
            String teacherId) {

        if (request == null || request.getOverrideScore() == null) {
            throw new IllegalArgumentException("Override score cannot be null");
        }

        BigDecimal score = request.getOverrideScore();
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Override score cannot be negative");
        }
        if (score.compareTo(BigDecimal.valueOf(10.00)) > 0) {
            throw new IllegalArgumentException("Override score cannot exceed 10.00");
        }
        if (score.scale() > 2) {
            throw new IllegalArgumentException("Override score cannot have more than 2 decimal places");
        }

        // Lock hierarchy: Exam -> ExamQuestion -> StudentQuestionScore
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        verifyTeacherAccess(exam, teacherId);

        ExamQuestion question = examQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        if (!question.getExam().getId().equals(examId)) {
            throw new IllegalArgumentException("Question " + questionId + " does not belong to exam " + examId);
        }

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));

        StudentQuestionScore sqs = studentQuestionScoreRepository.findByQuestion_IdAndStudent_IdForUpdate(questionId, studentId)
                .orElseGet(() -> {
                    StudentQuestionScore newSqs = StudentQuestionScore.builder()
                            .exam(exam)
                            .question(question)
                            .student(student)
                            .latestStatus("GRADED")
                            .effectiveScore(score.setScale(2, RoundingMode.HALF_UP))
                            .overrideScore(score.setScale(2, RoundingMode.HALF_UP))
                            .overrideReason(request.getRationale())
                            .overriddenBy(teacherId)
                            .overriddenAt(LocalDateTime.now())
                            .submissionCount(0)
                            .latestSubmissionSeq(0)
                            .build();
                    return studentQuestionScoreRepository.save(newSqs);
                });

        BigDecimal preRubric = sqs.getAutoScore();
        BigDecimal preOverride = sqs.getOverrideScore();
        BigDecimal preEffective = sqs.getEffectiveScore();
        BigDecimal postOverride = score.setScale(2, RoundingMode.HALF_UP);
        BigDecimal postEffective = postOverride;

        String actionType = preOverride == null ? "OVERRIDE_SET" : "OVERRIDE_UPDATE";

        sqs.setOverrideScore(postOverride);
        sqs.setEffectiveScore(postEffective);
        sqs.setOverrideReason(request.getRationale());
        sqs.setOverriddenBy(teacherId);
        sqs.setOverriddenAt(LocalDateTime.now());
        sqs = studentQuestionScoreRepository.save(sqs);

        // Sync with latest submission if present and record GradeAuditLog
        Submission latestSub = null;
        if (sqs.getLatestSubmissionId() != null) {
            latestSub = submissionRepository.findById(sqs.getLatestSubmissionId()).orElse(null);
            if (latestSub != null) {
                latestSub.setOverrideScore(postOverride);
                submissionRepository.save(latestSub);
            }
        }

        if (latestSub != null) {
            GradeAuditLog auditLog = GradeAuditLog.builder()
                    .submission(latestSub)
                    .actor(teacher)
                    .actionType(actionType)
                    .preRubricScore(preRubric)
                    .postRubricScore(preRubric)
                    .preOverrideScore(preOverride)
                    .postOverrideScore(postOverride)
                    .preEffectiveScore(preEffective)
                    .postEffectiveScore(postEffective)
                    .rationale(request.getRationale() != null ? request.getRationale().trim() : "Instructor score override")
                    .build();
            gradeAuditLogRepository.save(auditLog);
        }

        return QuestionScoreDetailDto.builder()
                .questionId(question.getId())
                .questionTitle(question.getTitle())
                .displayOrder(question.getDisplayOrder())
                .maxScore(question.getMaxScore())
                .autoScore(sqs.getAutoScore())
                .overrideScore(sqs.getOverrideScore())
                .effectiveScore(sqs.getEffectiveScore())
                .overrideReason(sqs.getOverrideReason())
                .overriddenBy(sqs.getOverriddenBy())
                .overriddenAt(sqs.getOverriddenAt())
                .status(sqs.getLatestStatus())
                .isOverridden(true)
                .latestSubmissionId(sqs.getLatestSubmissionId())
                .submissionCount(sqs.getSubmissionCount())
                .lastSubmittedAt(sqs.getLastSubmittedAt())
                .build();
    }

    @Transactional
    public QuestionScoreDetailDto clearQuestionOverride(
            String examId,
            String questionId,
            String studentId,
            String teacherId) {

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        verifyTeacherAccess(exam, teacherId);

        ExamQuestion question = examQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        if (!question.getExam().getId().equals(examId)) {
            throw new IllegalArgumentException("Question " + questionId + " does not belong to exam " + examId);
        }

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));

        StudentQuestionScore sqs = studentQuestionScoreRepository.findByQuestion_IdAndStudent_IdForUpdate(questionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("No score record found for question: " + questionId));

        BigDecimal preRubric = sqs.getAutoScore();
        BigDecimal preOverride = sqs.getOverrideScore();
        BigDecimal preEffective = sqs.getEffectiveScore();
        BigDecimal postOverride = null;
        BigDecimal postEffective = sqs.getAutoScore() != null
                ? sqs.getAutoScore().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        sqs.setOverrideScore(null);
        sqs.setEffectiveScore(postEffective);
        sqs.setOverrideReason(null);
        sqs.setOverriddenBy(null);
        sqs.setOverriddenAt(null);
        sqs = studentQuestionScoreRepository.save(sqs);

        // Sync with submission if present
        Submission latestSub = null;
        if (sqs.getLatestSubmissionId() != null) {
            latestSub = submissionRepository.findById(sqs.getLatestSubmissionId()).orElse(null);
            if (latestSub != null) {
                latestSub.setOverrideScore(null);
                submissionRepository.save(latestSub);
            }
        }

        if (latestSub != null && preOverride != null) {
            GradeAuditLog auditLog = GradeAuditLog.builder()
                    .submission(latestSub)
                    .actor(teacher)
                    .actionType("OVERRIDE_CLEARED")
                    .preRubricScore(preRubric)
                    .postRubricScore(preRubric)
                    .preOverrideScore(preOverride)
                    .postOverrideScore(null)
                    .preEffectiveScore(preEffective)
                    .postEffectiveScore(postEffective)
                    .rationale("Instructor cleared manual score override")
                    .build();
            gradeAuditLogRepository.save(auditLog);
        }

        return QuestionScoreDetailDto.builder()
                .questionId(question.getId())
                .questionTitle(question.getTitle())
                .displayOrder(question.getDisplayOrder())
                .maxScore(question.getMaxScore())
                .autoScore(sqs.getAutoScore())
                .overrideScore(null)
                .effectiveScore(sqs.getEffectiveScore())
                .overrideReason(null)
                .overriddenBy(null)
                .overriddenAt(null)
                .status(sqs.getLatestStatus())
                .isOverridden(false)
                .latestSubmissionId(sqs.getLatestSubmissionId())
                .submissionCount(sqs.getSubmissionCount())
                .lastSubmittedAt(sqs.getLastSubmittedAt())
                .build();
    }
}

