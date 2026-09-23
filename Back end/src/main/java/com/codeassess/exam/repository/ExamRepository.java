package com.codeassess.exam.repository;

import com.codeassess.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRepository extends JpaRepository<Exam, String> {

    List<Exam> findByCourseClass_Teacher_Id(String teacherId);

    Optional<Exam> findByIdAndCourseClass_Teacher_Id(String id, String teacherId);

    @Query("SELECT e FROM Exam e JOIN ClassStudent cs ON e.courseClass.id = cs.courseClass.id WHERE cs.student.id = :studentId")
    List<Exam> findByStudentId(@Param("studentId") String studentId);

    @Query("SELECT e FROM Exam e JOIN ClassStudent cs ON e.courseClass.id = cs.courseClass.id WHERE e.id = :id AND cs.student.id = :studentId")
    Optional<Exam> findByIdAndStudentId(@Param("id") String id, @Param("studentId") String studentId);
}
