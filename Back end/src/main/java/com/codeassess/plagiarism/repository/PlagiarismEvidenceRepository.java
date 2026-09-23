package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlagiarismEvidenceRepository extends JpaRepository<PlagiarismEvidence, String> {
    void deleteBySimilarityPair_Id(Long similarityPairId);
    List<PlagiarismEvidence> findBySimilarityPair_Id(Long similarityPairId);
}
