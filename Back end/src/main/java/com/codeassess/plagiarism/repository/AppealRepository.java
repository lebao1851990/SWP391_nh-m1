package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {

    java.util.List<Appeal> findByStudent_Id(String studentId);

    java.util.Optional<Appeal> findByIdAndStudent_Id(Long id, String studentId);

    boolean existsBySubmission_IdAndStatus(String submissionId, String status);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM Appeal a WHERE a.submission.exam.courseClass.teacher.id = :teacherId ORDER BY a.createdAt DESC")
    java.util.List<Appeal> findByTeacherId(@org.springframework.data.repository.query.Param("teacherId") String teacherId);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM Appeal a WHERE a.id = :id AND a.submission.exam.courseClass.teacher.id = :teacherId")
    java.util.Optional<Appeal> findByIdAndTeacherId(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("teacherId") String teacherId);
}
