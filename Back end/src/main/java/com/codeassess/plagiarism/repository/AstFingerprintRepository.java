package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.AstFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AstFingerprintRepository extends JpaRepository<AstFingerprint, Long> {
    void deleteBySubmission_Id(String submissionId);
    List<AstFingerprint> findBySubmission_Id(String submissionId);
}
