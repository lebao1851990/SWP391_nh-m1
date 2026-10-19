package com.codeassess.scoring.repository;

import com.codeassess.scoring.entity.GradeAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradeAuditLogRepository extends JpaRepository<GradeAuditLog, String> {
    List<GradeAuditLog> findBySubmission_IdOrderByCreatedAtDesc(String submissionId);
}
