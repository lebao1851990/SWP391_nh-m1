package com.codeassess.classes.repository;

import com.codeassess.classes.entity.ClassStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassStudentRepository extends JpaRepository<ClassStudent, ClassStudent.ClassStudentId> {
    long countByCourseClass_Id(String classId);

    @Query("SELECT COUNT(DISTINCT cs.student.id) FROM ClassStudent cs WHERE cs.courseClass.id IN :classIds")
    long countDistinctStudentsByClassIds(@Param("classIds") Collection<String> classIds);

    @Query("SELECT cs FROM ClassStudent cs JOIN FETCH cs.student WHERE cs.courseClass.id = :classId ORDER BY cs.student.fullName ASC, cs.student.email ASC")
    List<ClassStudent> findByCourseClassIdWithStudent(@Param("classId") String classId);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.courseClass.id = :classId AND cs.student.id = :studentId")
    Optional<ClassStudent> findByClassIdAndStudentId(@Param("classId") String classId, @Param("studentId") String studentId);

    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END FROM ClassStudent cs WHERE cs.courseClass.id = :classId AND cs.student.id = :studentId")
    boolean existsByClassIdAndStudentId(@Param("classId") String classId, @Param("studentId") String studentId);
}
