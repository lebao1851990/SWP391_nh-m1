package com.codeassess.classes.repository;

import com.codeassess.classes.entity.CourseClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseClassRepository extends JpaRepository<CourseClass, String> {

    List<CourseClass> findByTeacherId(String teacherId);

    Optional<CourseClass> findByIdAndTeacherId(String id, String teacherId);

    Optional<CourseClass> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT c FROM CourseClass c JOIN ClassStudent cs ON c.id = cs.courseClass.id WHERE cs.student.id = :studentId")
    List<CourseClass> findByStudentId(@Param("studentId") String studentId);

    @Query("SELECT c FROM CourseClass c JOIN ClassStudent cs ON c.id = cs.courseClass.id WHERE c.id = :id AND cs.student.id = :studentId")
    Optional<CourseClass> findByIdAndStudentId(@Param("id") String id, @Param("studentId") String studentId);
}
