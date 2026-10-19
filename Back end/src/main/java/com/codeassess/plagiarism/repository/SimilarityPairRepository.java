package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.SimilarityPair;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SimilarityPairRepository extends JpaRepository<SimilarityPair, Long> {
    List<SimilarityPair> findByExam_Id(String examId);
    List<SimilarityPair> findByExam_IdAndIsActiveTrue(String examId);
    Optional<SimilarityPair> findBySubmissionA_IdAndSubmissionB_Id(String submissionAId, String submissionBId);
    List<SimilarityPair> findBySubmissionA_IdOrSubmissionB_Id(String submissionAId, String submissionBId);

    @Query("SELECT sp FROM SimilarityPair sp WHERE sp.id = :id AND (sp.submissionA.student.id = :studentId OR sp.submissionB.student.id = :studentId)")
    Optional<SimilarityPair> findByIdAndStudentId(@Param("id") Long id, @Param("studentId") String studentId);

    @Query("SELECT sp FROM SimilarityPair sp JOIN sp.exam e JOIN e.courseClass cc WHERE cc.teacher.id = :teacherId ORDER BY sp.createdAt DESC")
    List<SimilarityPair> findByTeacherId(@Param("teacherId") String teacherId);

    @Query("SELECT DISTINCT sp FROM SimilarityPair sp " +
           "JOIN sp.exam e " +
           "LEFT JOIN e.courseClass cc " +
           "LEFT JOIN e.classes clc " +
           "WHERE (cc.teacher.id = :teacherId OR clc.teacher.id = :teacherId) " +
           "AND sp.isActive = true ORDER BY sp.createdAt DESC")
    List<SimilarityPair> findActiveByTeacherId(@Param("teacherId") String teacherId);

    @Query("SELECT DISTINCT sp FROM SimilarityPair sp " +
           "JOIN sp.exam e " +
           "LEFT JOIN e.courseClass cc " +
           "LEFT JOIN e.classes clc " +
           "WHERE (cc.teacher.id = :teacherId OR clc.teacher.id = :teacherId) " +
           "ORDER BY sp.createdAt DESC")
    List<SimilarityPair> findAllByTeacherId(@Param("teacherId") String teacherId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = false)
    @Query("UPDATE SimilarityPair sp SET " +
           "sp.similarityRate = :rate, " +
           "sp.sharedHashesCount = :hashes, " +
           "sp.isFlagged = :flagged, " +
           "sp.isActive = true " +
           "WHERE sp.id = :id")
    int updateCalculatedMetrics(
        @Param("id") Long id,
        @Param("rate") BigDecimal rate,
        @Param("hashes") Integer hashes,
        @Param("flagged") Boolean flagged
    );

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = false)
    @Query("UPDATE SimilarityPair sp SET sp.isActive = false WHERE sp.id IN :ids")
    int deactivatePairsByIds(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SimilarityPair sp WHERE sp.id = :id")
    Optional<SimilarityPair> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT sp FROM SimilarityPair sp " +
           "JOIN sp.exam e " +
           "LEFT JOIN e.courseClass cc " +
           "LEFT JOIN e.classes clc " +
           "WHERE sp.id = :id AND (cc.teacher.id = :teacherId OR clc.teacher.id = :teacherId)")
    Optional<SimilarityPair> findByIdAndTeacherIdForUpdate(@Param("id") Long id, @Param("teacherId") String teacherId);
}
