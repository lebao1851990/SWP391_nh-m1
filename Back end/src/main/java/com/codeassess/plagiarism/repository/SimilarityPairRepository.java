package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.SimilarityPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface SimilarityPairRepository extends JpaRepository<SimilarityPair, Long> {
    List<SimilarityPair> findByExam_Id(String examId);
    Optional<SimilarityPair> findBySubmissionA_IdAndSubmissionB_Id(String submissionAId, String submissionBId);
    List<SimilarityPair> findBySubmissionA_IdOrSubmissionB_Id(String submissionAId, String submissionBId);

    @Query("SELECT sp FROM SimilarityPair sp WHERE sp.id = :id AND (sp.submissionA.student.id = :studentId OR sp.submissionB.student.id = :studentId)")
    Optional<SimilarityPair> findByIdAndStudentId(@Param("id") Long id, @Param("studentId") String studentId);
}
