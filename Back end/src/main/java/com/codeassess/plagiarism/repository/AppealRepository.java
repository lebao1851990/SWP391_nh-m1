package com.codeassess.plagiarism.repository;

import com.codeassess.plagiarism.entity.Appeal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {

    List<Appeal> findByStudent_Id(String studentId);

    Optional<Appeal> findByIdAndStudent_Id(Long id, String studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appeal a WHERE a.id = :id")
    Optional<Appeal> findByIdForUpdate(@Param("id") Long id);

    boolean existsBySubmission_IdAndStatus(String submissionId, String status);

    boolean existsBySubmission_IdAndStatusIn(String submissionId, Collection<String> statuses);

    @Query("SELECT DISTINCT a FROM Appeal a LEFT JOIN a.submission.exam.classes c WHERE (a.submission.exam.courseClass.teacher.id = :teacherId OR c.teacher.id = :teacherId) ORDER BY a.createdAt DESC")
    List<Appeal> findByTeacherId(@Param("teacherId") String teacherId);

    @Query("SELECT DISTINCT a FROM Appeal a LEFT JOIN a.submission.exam.classes c WHERE a.id = :id AND (a.submission.exam.courseClass.teacher.id = :teacherId OR c.teacher.id = :teacherId)")
    Optional<Appeal> findByIdAndTeacherId(@Param("id") Long id, @Param("teacherId") String teacherId);

    List<Appeal> findAllByOrderByCreatedAtDesc();

    List<Appeal> findByStatusOrderByCreatedAtDesc(String status);
}
