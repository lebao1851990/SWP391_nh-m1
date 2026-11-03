package com.codeassess.scoring.repository;

import com.codeassess.scoring.entity.StudentQuestionScore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentQuestionScoreRepository extends JpaRepository<StudentQuestionScore, String> {

    List<StudentQuestionScore> findByExam_IdAndStudent_Id(String examId, String studentId);

    Optional<StudentQuestionScore> findByQuestion_IdAndStudent_Id(String questionId, String studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sqs FROM StudentQuestionScore sqs WHERE sqs.question.id = :questionId AND sqs.student.id = :studentId")
    Optional<StudentQuestionScore> findByQuestion_IdAndStudent_IdForUpdate(
            @Param("questionId") String questionId,
            @Param("studentId") String studentId);

    /**
     * Native atomic upsert for submission intake.
     * Guarantees monotonic sequence progression, accurate count, and safe initialization.
     */
    @Modifying
    @Query(value = """
        INSERT INTO student_question_scores (
            id, exam_id, question_id, student_id,
            latest_submission_id, latest_submission_seq, latest_status,
            submission_count, effective_score, auto_score, override_score,
            override_reason, overridden_by, overridden_at, last_submitted_at,
            created_at, updated_at
        ) VALUES (
            :id, :examId, :questionId, :studentId,
            :submissionId, 1, 'PENDING',
            1, 0.00, NULL, NULL,
            NULL, NULL, NULL, NOW(),
            NOW(), NOW()
        )
        ON DUPLICATE KEY UPDATE
            latest_submission_id = VALUES(latest_submission_id),
            latest_submission_seq = student_question_scores.latest_submission_seq + 1,
            latest_status = 'PENDING',
            submission_count = student_question_scores.submission_count + 1,
            last_submitted_at = VALUES(last_submitted_at),
            updated_at = NOW()
        """, nativeQuery = true)
    int upsertIntakeSequence(
            @Param("id") String id,
            @Param("examId") String examId,
            @Param("questionId") String questionId,
            @Param("studentId") String studentId,
            @Param("submissionId") String submissionId);

    /**
     * Fenced worker completion update.
     * Only updates effective_score if the completing submission is still the latest one.
     */
    @Modifying
    @Query("""
        UPDATE StudentQuestionScore sqs
        SET sqs.autoScore = :autoScore,
            sqs.effectiveScore = COALESCE(sqs.overrideScore, :autoScore, 0.00),
            sqs.latestStatus = 'COMPLETED',
            sqs.updatedAt = CURRENT_TIMESTAMP
        WHERE sqs.question.id = :questionId
          AND sqs.student.id = :studentId
          AND sqs.latestSubmissionId = :submissionId
        """)
    int completeWorkerGradingIfLatest(
            @Param("questionId") String questionId,
            @Param("studentId") String studentId,
            @Param("submissionId") String submissionId,
            @Param("autoScore") BigDecimal autoScore);

    /**
     * Fenced failure status update.
     * Only updates latestStatus to status if the failing submission is still the latest one.
     * Preserves latest_submission_seq, submission_count, latest_submission_id, effective_score, and override_score.
     */
    @Modifying
    @Query("""
        UPDATE StudentQuestionScore sqs
        SET sqs.latestStatus = :status,
            sqs.updatedAt = CURRENT_TIMESTAMP
        WHERE sqs.question.id = :questionId
          AND sqs.student.id = :studentId
          AND sqs.latestSubmissionId = :submissionId
        """)
    int updateFailureStatusIfLatest(
            @Param("questionId") String questionId,
            @Param("studentId") String studentId,
            @Param("submissionId") String submissionId,
            @Param("status") String status);

    @Modifying
    @Query("""
        UPDATE StudentQuestionScore sqs
        SET sqs.overrideScore = :overrideScore,
            sqs.effectiveScore = :overrideScore,
            sqs.overrideReason = :overrideReason,
            sqs.overriddenBy = :teacherId,
            sqs.overriddenAt = CURRENT_TIMESTAMP,
            sqs.updatedAt = CURRENT_TIMESTAMP
        WHERE sqs.question.id = :questionId
          AND sqs.student.id = :studentId
        """)
    int setQuestionOverride(
            @Param("questionId") String questionId,
            @Param("studentId") String studentId,
            @Param("overrideScore") BigDecimal overrideScore,
            @Param("overrideReason") String overrideReason,
            @Param("teacherId") String teacherId);

    @Modifying
    @Query("""
        UPDATE StudentQuestionScore sqs
        SET sqs.overrideScore = NULL,
            sqs.effectiveScore = COALESCE(sqs.autoScore, 0.00),
            sqs.overrideReason = NULL,
            sqs.overriddenBy = NULL,
            sqs.overriddenAt = NULL,
            sqs.updatedAt = CURRENT_TIMESTAMP
        WHERE sqs.question.id = :questionId
          AND sqs.student.id = :studentId
        """)
    int clearQuestionOverride(
            @Param("questionId") String questionId,
            @Param("studentId") String studentId);
}

