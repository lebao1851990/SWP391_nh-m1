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

    List<Submission> findByExam_CourseClass_Teacher_Id(String teacherId);

    Optional<Submission> findByIdAndExam_CourseClass_Teacher_Id(String id, String teacherId);

    List<Submission> findByStudent_Id(String studentId);

    Optional<Submission> findByIdAndStudent_Id(String id, String studentId);

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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Submission s SET s.status = 'PENDING', s.claimToken = NULL, s.leaseExpiresAt = NULL WHERE s.id = :id AND s.status = 'PROCESSING' AND s.claimToken = :claimToken")
    int releaseExpiredClaim(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("claimToken") String claimToken);

    List<Submission> findByStatusAndCreatedAtBefore(String status, java.time.LocalDateTime cutoff);

    List<Submission> findByStatusAndLeaseExpiresAtBefore(String status, java.time.LocalDateTime cutoff);

    List<Submission> findByStatusInAndCreatedAtBefore(List<String> statuses, java.time.LocalDateTime cutoff);
}
