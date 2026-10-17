package com.codeassess.exam.repository;

import com.codeassess.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRepository extends JpaRepository<Exam, String> {

    @Query("SELECT DISTINCT e FROM Exam e LEFT JOIN e.classes c WHERE e.courseClass.teacher.id = :teacherId OR c.teacher.id = :teacherId ORDER BY e.createdAt DESC")
    List<Exam> findByCourseClass_Teacher_Id(@Param("teacherId") String teacherId);

    @Query("SELECT DISTINCT e FROM Exam e LEFT JOIN e.classes c WHERE e.id = :id AND (e.courseClass.teacher.id = :teacherId OR c.teacher.id = :teacherId)")
    Optional<Exam> findByIdAndCourseClass_Teacher_Id(@Param("id") String id, @Param("teacherId") String teacherId);

    @Query("SELECT DISTINCT e FROM Exam e LEFT JOIN e.classes c JOIN ClassStudent cs ON (c.id = cs.courseClass.id OR e.courseClass.id = cs.courseClass.id) WHERE cs.student.id = :studentId ORDER BY e.examDate ASC")
    List<Exam> findByStudentId(@Param("studentId") String studentId);

    @Query("SELECT DISTINCT e FROM Exam e LEFT JOIN e.classes c JOIN ClassStudent cs ON (c.id = cs.courseClass.id OR e.courseClass.id = cs.courseClass.id) WHERE e.id = :id AND cs.student.id = :studentId")
    Optional<Exam> findByIdAndStudentId(@Param("id") String id, @Param("studentId") String studentId);
}
