package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {

    java.util.List<Appeal> findByStudent_Id(String studentId);

    java.util.Optional<Appeal> findByIdAndStudent_Id(Long id, String studentId);

    boolean existsBySubmission_IdAndStatus(String submissionId, String status);
}
