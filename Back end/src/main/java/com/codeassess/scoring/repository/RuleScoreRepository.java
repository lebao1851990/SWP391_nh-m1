package com.codeassess.scoring.repository;

import com.codeassess.scoring.entity.RuleScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleScoreRepository extends JpaRepository<RuleScore, String> {
    List<RuleScore> findBySubmission_Id(String submissionId);
    void deleteBySubmission_Id(String submissionId);
}
