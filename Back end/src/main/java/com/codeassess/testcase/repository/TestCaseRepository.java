package com.codeassess.testcase.repository;

import com.codeassess.testcase.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, String> {

    List<TestCase> findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(String examId);

    Optional<TestCase> findByIdAndExam_Id(String id, String examId);

    long countByExam_Id(String examId);

    void deleteByExam_Id(String examId);
}
