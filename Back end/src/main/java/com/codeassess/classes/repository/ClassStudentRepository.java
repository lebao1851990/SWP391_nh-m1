package com.codeassess.classes.repository;

import com.codeassess.classes.entity.ClassStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassStudentRepository extends JpaRepository<ClassStudent, ClassStudent.ClassStudentId> {
    long countByCourseClass_Id(String classId);
}
