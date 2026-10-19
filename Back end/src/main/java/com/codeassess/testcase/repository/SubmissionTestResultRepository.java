package com.codeassess.testcase.repository;

import com.codeassess.testcase.entity.SubmissionTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, String> {

    List<SubmissionTestResult> findBySubmission_IdOrderByIdAsc(String submissionId);

    void deleteBySubmission_Id(String submissionId);
}
