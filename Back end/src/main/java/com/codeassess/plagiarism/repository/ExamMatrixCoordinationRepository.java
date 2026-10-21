package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.ExamMatrixCoordination;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamMatrixCoordinationRepository extends JpaRepository<ExamMatrixCoordination, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ExamMatrixCoordination c WHERE c.examId = :examId")
    Optional<ExamMatrixCoordination> findByExamIdForUpdate(@Param("examId") String examId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query(value = """
        INSERT INTO exam_matrix_coordination (exam_id, current_generation, requested_generation, locked_generation, status, failure_count, last_error, next_retry_at)
        VALUES (:examId, 0, 1, 0, 'IDLE', 0, NULL, NULL)
        ON DUPLICATE KEY UPDATE 
            requested_generation = requested_generation + 1,
            failure_count = CASE WHEN status = 'FAILED' THEN 0 ELSE failure_count END,
            last_error = CASE WHEN status = 'FAILED' THEN NULL ELSE last_error END,
            next_retry_at = CASE WHEN status = 'FAILED' THEN NULL ELSE next_retry_at END,
            status = CASE WHEN status = 'FAILED' THEN 'IDLE' ELSE status END
        """, nativeQuery = true)
    int requestMatrixRefresh(@Param("examId") String examId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query(value = """
        INSERT INTO exam_matrix_coordination (exam_id, current_generation, requested_generation, locked_generation, status, failure_count)
        VALUES (:examId, 0, 1, 0, 'IDLE', 0)
        ON DUPLICATE KEY UPDATE exam_id = exam_id
        """, nativeQuery = true)
    int initCoordinationIfAbsent(@Param("examId") String examId);

    @Query(value = """
        SELECT exam_id FROM exam_matrix_coordination
        WHERE (status = 'IDLE' AND requested_generation > current_generation AND (next_retry_at IS NULL OR next_retry_at <= NOW()))
           OR (status = 'RUNNING' AND lease_expires_at < NOW())
        """, nativeQuery = true)
    List<String> findExamsRequiringRefreshRecovery();
}
