package com.codeassess.scoring.repository;

import com.codeassess.scoring.entity.ScoringCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScoringCriteriaRepository extends JpaRepository<ScoringCriteria, String> {
}
