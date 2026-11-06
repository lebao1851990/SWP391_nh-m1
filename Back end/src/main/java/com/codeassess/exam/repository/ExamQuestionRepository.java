package com.codeassess.exam.repository;

import com.codeassess.exam.entity.ExamQuestion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, String> {

    List<ExamQuestion> findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(String examId);

    Optional<ExamQuestion> findByIdAndExam_Id(String id, String examId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT eq FROM ExamQuestion eq WHERE eq.id = :id AND eq.exam.id = :examId")
    Optional<ExamQuestion> findByIdAndExam_IdForShare(@Param("id") String id, @Param("examId") String examId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT eq FROM ExamQuestion eq WHERE eq.id = :id AND eq.exam.id = :examId")
    Optional<ExamQuestion> findByIdAndExam_IdForUpdate(@Param("id") String id, @Param("examId") String examId);

    long countByExam_Id(String examId);
}

