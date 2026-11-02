package com.codeassess.scoring.repository;

import com.codeassess.scoring.entity.ExamRubricCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRubricCriteriaRepository extends JpaRepository<ExamRubricCriteria, String> {
    List<ExamRubricCriteria> findByExam_IdOrderByDisplayOrderAsc(String examId);
}
