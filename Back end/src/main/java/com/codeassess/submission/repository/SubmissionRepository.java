package com.codeassess.submission.repository;

import com.codeassess.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, String> {

    List<Submission> findByExam_Id(String examId);
    
    long countByExam_Id(String examId);

    List<Submission> findByExam_CourseClass_Teacher_Id(String teacherId);

    Optional<Submission> findByIdAndExam_CourseClass_Teacher_Id(String id, String teacherId);

    List<Submission> findByStudent_Id(String studentId);

    Optional<Submission> findByIdAndStudent_Id(String id, String studentId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Submission s WHERE s.id = :id AND s.student.id = :studentId")
    Optional<Submission> findByIdAndStudent_IdForUpdate(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("studentId") String studentId);
}
