package com.codeassess.plagiarism.repository;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class SimilarityPairRepositoryDataJpaTest {

    @Autowired
    private SimilarityPairRepository similarityPairRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseClassRepository courseClassRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Test
    @DisplayName("findByTeacherId executes real JPQL JOIN and returns only pairs belonging to teacher's classes in createdAt DESC order")
    public void testFindByTeacherId_RealDatabaseQuery() {
        // Teacher A
        User teacherA = userRepository.save(User.builder()
                .email("teacherA_repo_test@fpt.edu.vn")
                .fullName("Teacher A")
                .role(Role.TEACHER)
                .passwordHash("hash")
                .build());

        // Teacher B
        User teacherB = userRepository.save(User.builder()
                .email("teacherB_repo_test@fpt.edu.vn")
                .fullName("Teacher B")
                .role(Role.TEACHER)
                .passwordHash("hash")
                .build());

        // Student A1, A2 for Teacher A
        User studentA1 = userRepository.save(User.builder()
                .email("studentA1_repo@fpt.edu.vn")
                .fullName("Student A1")
                .role(Role.STUDENT)
                .passwordHash("hash")
                .build());
        User studentA2 = userRepository.save(User.builder()
                .email("studentA2_repo@fpt.edu.vn")
                .fullName("Student A2")
                .role(Role.STUDENT)
                .passwordHash("hash")
                .build());

        // Student B1, B2 for Teacher B
        User studentB1 = userRepository.save(User.builder()
                .email("studentB1_repo@fpt.edu.vn")
                .fullName("Student B1")
                .role(Role.STUDENT)
                .passwordHash("hash")
                .build());
        User studentB2 = userRepository.save(User.builder()
                .email("studentB2_repo@fpt.edu.vn")
                .fullName("Student B2")
                .role(Role.STUDENT)
                .passwordHash("hash")
                .build());

        // CourseClass A (Teacher A) and CourseClass B (Teacher B)
        CourseClass classA = courseClassRepository.save(CourseClass.builder()
                .name("Class Repo A")
                .code("CRA_" + UUID.randomUUID().toString().substring(0, 8))
                .teacher(teacherA)
                .build());

        CourseClass classB = courseClassRepository.save(CourseClass.builder()
                .name("Class Repo B")
                .code("CRB_" + UUID.randomUUID().toString().substring(0, 8))
                .teacher(teacherB)
                .build());

        // Exam A and Exam B
        Exam examA = examRepository.save(Exam.builder()
                .title("Exam Repo A")
                .courseClass(classA)
                .examDate(LocalDateTime.now().plusDays(1))
                .duration(60)
                .status("ACTIVE")
                .build());

        Exam examB = examRepository.save(Exam.builder()
                .title("Exam Repo B")
                .courseClass(classB)
                .examDate(LocalDateTime.now().plusDays(1))
                .duration(60)
                .status("ACTIVE")
                .build());

        // Submissions for Exam A
        Submission subA1 = submissionRepository.save(Submission.builder()
                .id(UUID.randomUUID().toString())
                .exam(examA)
                .student(studentA1)
                .sourceCode("codeA1")
                .status("GRADED")
                .build());
        Submission subA2 = submissionRepository.save(Submission.builder()
                .id(UUID.randomUUID().toString())
                .exam(examA)
                .student(studentA2)
                .sourceCode("codeA2")
                .status("GRADED")
                .build());

        // Submissions for Exam B
        Submission subB1 = submissionRepository.save(Submission.builder()
                .id(UUID.randomUUID().toString())
                .exam(examB)
                .student(studentB1)
                .sourceCode("codeB1")
                .status("GRADED")
                .build());
        Submission subB2 = submissionRepository.save(Submission.builder()
                .id(UUID.randomUUID().toString())
                .exam(examB)
                .student(studentB2)
                .sourceCode("codeB2")
                .status("GRADED")
                .build());

        // SimilarityPair for Teacher A
        SimilarityPair pairA = similarityPairRepository.save(SimilarityPair.builder()
                .exam(examA)
                .submissionA(subA1)
                .submissionB(subA2)
                .similarityRate(new BigDecimal("82.50"))
                .sharedHashesCount(15)
                .isFlagged(true)
                .build());

        // SimilarityPair for Teacher B
        SimilarityPair pairB = similarityPairRepository.save(SimilarityPair.builder()
                .exam(examB)
                .submissionA(subB1)
                .submissionB(subB2)
                .similarityRate(new BigDecimal("91.00"))
                .sharedHashesCount(22)
                .isFlagged(true)
                .build());

        // Execute real JPQL query for Teacher A
        List<SimilarityPair> teacherAPairs = similarityPairRepository.findByTeacherId(teacherA.getId());
        assertThat(teacherAPairs).isNotEmpty();
        assertThat(teacherAPairs).allMatch(p -> p.getExam().getCourseClass().getTeacher().getId().equals(teacherA.getId()));
        assertThat(teacherAPairs).noneMatch(p -> p.getExam().getCourseClass().getTeacher().getId().equals(teacherB.getId()));
        assertThat(teacherAPairs).extracting(SimilarityPair::getId).contains(pairA.getId());
        assertThat(teacherAPairs).extracting(SimilarityPair::getId).doesNotContain(pairB.getId());

        // Execute real JPQL query for Teacher B
        List<SimilarityPair> teacherBPairs = similarityPairRepository.findByTeacherId(teacherB.getId());
        assertThat(teacherBPairs).isNotEmpty();
        assertThat(teacherBPairs).allMatch(p -> p.getExam().getCourseClass().getTeacher().getId().equals(teacherB.getId()));
        assertThat(teacherBPairs).noneMatch(p -> p.getExam().getCourseClass().getTeacher().getId().equals(teacherA.getId()));
        assertThat(teacherBPairs).extracting(SimilarityPair::getId).contains(pairB.getId());
        assertThat(teacherBPairs).extracting(SimilarityPair::getId).doesNotContain(pairA.getId());

        // Non-existent teacher ID returns empty list
        List<SimilarityPair> nonExistent = similarityPairRepository.findByTeacherId(UUID.randomUUID().toString());
        assertThat(nonExistent).isEmpty();
    }
}
