package com.codeassess.submission.repository;

import com.codeassess.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, String> {

    List<Submission> findByExam_Id(String examId);
    
    long countByExam_Id(String examId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(s) FROM Submission s WHERE s.exam.id = :examId AND (s.status = 'PROCESSING' OR s.executionStatus = 'IN_PROGRESS')")
    long countActiveGradingSubmissionsByExamId(@org.springframework.data.repository.query.Param("examId") String examId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(s) FROM Submission s JOIN com.codeassess.classes.entity.ClassStudent cs ON cs.student.id = s.student.id WHERE s.exam.id = :examId AND cs.courseClass.id = :classId")
    long countSubmissionsByExamIdAndClassId(@org.springframework.data.repository.query.Param("examId") String examId, @org.springframework.data.repository.query.Param("classId") String classId);

    List<Submission> findByExam_CourseClass_Teacher_Id(String teacherId);

    Optional<Submission> findByIdAndExam_CourseClass_Teacher_Id(String id, String teacherId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT DISTINCT s FROM Submission s 
        LEFT JOIN s.exam.classes c 
        WHERE (s.exam.courseClass.teacher.id = :teacherId OR c.teacher.id = :teacherId) 
          AND (:reviewStatus IS NULL OR s.reviewStatus = :reviewStatus)
        ORDER BY s.createdAt DESC
    """)
    List<Submission> findByTeacherIdAndReviewStatus(
            @org.springframework.data.repository.query.Param("teacherId") String teacherId,
            @org.springframework.data.repository.query.Param("reviewStatus") String reviewStatus);

    @org.springframework.data.jpa.repository.Query("""
        SELECT DISTINCT s FROM Submission s 
        LEFT JOIN s.exam.classes c 
        WHERE (s.exam.courseClass.teacher.id = :teacherId OR c.teacher.id = :teacherId) 
          AND s.id = :submissionId
    """)
    Optional<Submission> findByIdAndTeacherAccess(
            @org.springframework.data.repository.query.Param("submissionId") String submissionId,
            @org.springframework.data.repository.query.Param("teacherId") String teacherId);

    List<Submission> findByStudent_Id(String studentId);

    Optional<Submission> findByIdAndStudent_Id(String id, String studentId);

    Optional<Submission> findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc(String examId, String studentId);

    List<Submission> findByExam_IdAndStudent_IdOrderByCreatedAtDesc(String examId, String studentId);

    boolean existsByStudent_IdAndExam_CourseClass_Id(String studentId, String classId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Submission s WHERE s.id = :id AND s.student.id = :studentId")
    Optional<Submission> findByIdAndStudent_IdForUpdate(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("studentId") String studentId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Submission s SET s.status = 'PROCESSING' WHERE s.id = :id AND s.status = 'PENDING'")
    int claimSubmissionForGrading(@org.springframework.data.repository.query.Param("id") String id);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Submission s SET s.status = 'PROCESSING', s.claimToken = :claimToken, s.leaseExpiresAt = :leaseExpiresAt WHERE s.id = :id AND s.status = 'PENDING'")
    int claimSubmissionWithToken(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("claimToken") String claimToken,
            @org.springframework.data.repository.query.Param("leaseExpiresAt") java.time.LocalDateTime leaseExpiresAt);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Submission s SET s.status = 'COMPLETED', s.executionStatus = :executionStatus, s.exitCode = :exitCode, s.compileOutput = :compileOutput, s.stdout = :stdout, s.stderr = :stderr, s.executionDurationMs = :duration, s.evaluatedAt = :completedAt, s.claimToken = NULL, s.leaseExpiresAt = NULL WHERE s.id = :id AND s.status = 'PROCESSING' AND s.claimToken = :claimToken")
    int completeGradingWithToken(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("claimToken") String claimToken,
            @org.springframework.data.repository.query.Param("executionStatus") String executionStatus,
            @org.springframework.data.repository.query.Param("exitCode") Integer exitCode,
            @org.springframework.data.repository.query.Param("compileOutput") String compileOutput,
            @org.springframework.data.repository.query.Param("stdout") String stdout,
            @org.springframework.data.repository.query.Param("stderr") String stderr,
            @org.springframework.data.repository.query.Param("duration") Long duration,
            @org.springframework.data.repository.query.Param("completedAt") java.time.LocalDateTime completedAt);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Submission s SET s.status = 'COMPLETED', s.evaluatedAt = :completedAt, s.claimToken = NULL, s.leaseExpiresAt = NULL WHERE s.id = :id AND s.status = 'PROCESSING' AND s.claimToken = :claimToken")
    int completeZipGradingWithToken(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("claimToken") String claimToken,
            @org.springframework.data.repository.query.Param("completedAt") java.time.LocalDateTime completedAt);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Submission s SET s.status = 'PENDING', s.claimToken = NULL, s.leaseExpiresAt = NULL WHERE s.id = :id AND s.status = 'PROCESSING' AND s.claimToken = :claimToken")
    int releaseExpiredClaim(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("claimToken") String claimToken);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Submission s
        SET s.status = 'FAILED',
            s.executionStatus = :executionStatus,
            s.compileOutput = :compileOutput,
            s.claimToken = NULL,
            s.leaseExpiresAt = NULL
        WHERE s.id = :id
          AND s.status = 'PROCESSING'
          AND s.claimToken = :claimToken
    """)
    int failProcessingSubmissionWithToken(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("claimToken") String claimToken,
            @org.springframework.data.repository.query.Param("executionStatus") String executionStatus,
            @org.springframework.data.repository.query.Param("compileOutput") String compileOutput);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Submission s
        SET s.claimToken = :recoveryToken,
            s.leaseExpiresAt = :recoveryLease
        WHERE s.id = :id
          AND s.status = 'PENDING'
          AND (s.executionStatus IS NULL OR s.executionStatus != 'DISPATCHED')
          AND (s.claimToken IS NULL OR s.leaseExpiresAt < :now)
    """)
    int claimSubmissionForRecovery(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("recoveryToken") String recoveryToken,
            @org.springframework.data.repository.query.Param("recoveryLease") java.time.LocalDateTime recoveryLease,
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Submission s
        SET s.claimToken = NULL,
            s.leaseExpiresAt = NULL
        WHERE s.id = :id
          AND s.claimToken = :recoveryToken
          AND s.status = 'PENDING'
    """)
    int releaseRecoveryClaim(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("recoveryToken") String recoveryToken);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Submission s
        SET s.executionStatus = 'DISPATCHED'
        WHERE s.id = :id
          AND s.status = 'PENDING'
          AND s.claimToken IS NULL
    """)
    int recordSuccessfulInitialDispatch(@org.springframework.data.repository.query.Param("id") String id);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Submission s
        SET s.executionStatus = 'DISPATCHED',
            s.claimToken = NULL,
            s.leaseExpiresAt = NULL
        WHERE s.id = :id
          AND s.status = 'PENDING'
          AND s.claimToken = :recoveryToken
    """)
    int recordSuccessfulRecoveryDispatch(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("recoveryToken") String recoveryToken);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Submission s
        SET s.executionStatus = 'DISPATCHED',
            s.claimToken = NULL,
            s.leaseExpiresAt = NULL
        WHERE s.id = :id
          AND s.status = 'PENDING'
          AND ((:recoveryToken IS NULL AND s.claimToken IS NULL) OR s.claimToken = :recoveryToken)
    """)
    int recordSuccessfulDispatch(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("recoveryToken") String recoveryToken);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Submission s
        SET s.status = 'FAILED',
            s.executionStatus = :executionStatus,
            s.compileOutput = :compileOutput,
            s.claimToken = NULL,
            s.leaseExpiresAt = NULL
        WHERE s.id = :id
          AND s.status = 'PENDING'
          AND ((:claimToken IS NULL AND s.claimToken IS NULL) OR s.claimToken = :claimToken)
    """)
    int failPendingSubmission(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("claimToken") String claimToken,
            @org.springframework.data.repository.query.Param("executionStatus") String executionStatus,
            @org.springframework.data.repository.query.Param("compileOutput") String compileOutput);

    List<Submission> findByStatusAndCreatedAtBefore(String status, java.time.LocalDateTime cutoff);

    List<Submission> findByStatusAndLeaseExpiresAtBefore(String status, java.time.LocalDateTime cutoff);

    List<Submission> findByStatusInAndCreatedAtBefore(List<String> statuses, java.time.LocalDateTime cutoff);
}
