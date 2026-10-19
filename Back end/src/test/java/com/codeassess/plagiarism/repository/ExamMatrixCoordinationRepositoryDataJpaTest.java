package com.codeassess.plagiarism.repository;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.ExamMatrixCoordination;
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
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ExamMatrixCoordinationRepositoryDataJpaTest {

    @Autowired
    private ExamMatrixCoordinationRepository coordinationRepository;

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

    @Autowired
    private EntityManager entityManager;

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("V10 schema: ExamMatrixCoordination can be persisted, locked for update, and refreshed via atomic upsert")
    public void testCoordinationPersistenceAndAtomicUpsert() {
        String suffix = randomSuffix();
        User teacher = userRepository.save(User.builder()
                .email("teacher_coord_" + suffix + "@test.edu.vn")
                .fullName("Teacher Coord")
                .role(Role.TEACHER)
                .passwordHash("pwd")
                .build());

        CourseClass cc = courseClassRepository.save(CourseClass.builder()
                .code("CC_COORD_" + suffix)
                .name("Class Coord " + suffix)
                .teacher(teacher)
                .semester("FA24")
                .build());

        Exam exam = examRepository.save(Exam.builder()
                .title("Coord Exam " + suffix)
                .courseClass(cc)
                .status("ACTIVE")
                .build());

        // 1. Initial requestMatrixRefresh creates coordination row with requested_generation = 1
        int rowsAffected1 = coordinationRepository.requestMatrixRefresh(exam.getId());
        assertThat(rowsAffected1).isGreaterThanOrEqualTo(1);

        entityManager.clear();

        // 2. Read back coordination row
        Optional<ExamMatrixCoordination> coordOpt = coordinationRepository.findByExamIdForUpdate(exam.getId());
        assertThat(coordOpt).isPresent();
        ExamMatrixCoordination coord = coordOpt.get();
        assertThat(coord.getCurrentGeneration()).isEqualTo(0L);
        assertThat(coord.getRequestedGeneration()).isEqualTo(1L);
        assertThat(coord.getStatus()).isEqualTo("IDLE");

        // 3. Second requestMatrixRefresh increments requested_generation to 2
        int rowsAffected2 = coordinationRepository.requestMatrixRefresh(exam.getId());
        assertThat(rowsAffected2).isGreaterThanOrEqualTo(1);

        entityManager.clear();

        coordOpt = coordinationRepository.findById(exam.getId());
        assertThat(coordOpt).isPresent();
        assertThat(coordOpt.get().getRequestedGeneration()).isEqualTo(2L);

        // 4. Recovery query returns this exam because requested_generation (2) > current_generation (0)
        List<String> examsToRecover = coordinationRepository.findExamsRequiringRefreshRecovery();
        assertThat(examsToRecover).contains(exam.getId());
    }

    @Test
    @DisplayName("V10 schema: SimilarityPair isActive, targeted update, and soft deactivation work as expected")
    public void testSimilarityPairActiveFilterAndTargetedUpdate() {
        String suffix = randomSuffix();
        User teacher = userRepository.save(User.builder()
                .email("teacher_sp_" + suffix + "@test.edu.vn")
                .fullName("Teacher SP")
                .role(Role.TEACHER)
                .passwordHash("pwd")
                .build());

        CourseClass cc = courseClassRepository.save(CourseClass.builder()
                .code("CC_SP_" + suffix)
                .name("Class SP " + suffix)
                .teacher(teacher)
                .semester("FA24")
                .build());

        Exam exam = examRepository.save(Exam.builder()
                .title("SP Exam " + suffix)
                .courseClass(cc)
                .status("ACTIVE")
                .build());

        User stuA = userRepository.save(User.builder().email("sa_" + suffix + "@test.com").fullName("SA").passwordHash("pwd").role(Role.STUDENT).build());
        User stuB = userRepository.save(User.builder().email("sb_" + suffix + "@test.com").fullName("SB").passwordHash("pwd").role(Role.STUDENT).build());

        Submission sub1 = submissionRepository.save(Submission.builder().exam(exam).student(stuA).status("EVALUATED").sourceCode("code 1").build());
        Submission sub2 = submissionRepository.save(Submission.builder().exam(exam).student(stuB).status("EVALUATED").sourceCode("code 2").build());

        Submission subA = sub1.getId().compareTo(sub2.getId()) < 0 ? sub1 : sub2;
        Submission subB = sub1.getId().compareTo(sub2.getId()) < 0 ? sub2 : sub1;

        // Save pair with adjudication
        SimilarityPair pair = similarityPairRepository.save(SimilarityPair.builder()
                .exam(exam)
                .submissionA(subA)
                .submissionB(subB)
                .similarityRate(new BigDecimal("75.00"))
                .sharedHashesCount(15)
                .isFlagged(true)
                .adjudicationStatus("FLAG_CONFIRMED")
                .adjudicationNotes("Verified identical loops")
                .adjudicatedBy(teacher)
                .adjudicatedAt(LocalDateTime.now())
                .isActive(true)
                .build());

        Long pairId = pair.getId();
        assertThat(pairId).isNotNull();
        assertThat(pair.getIsActive()).isTrue();

        // 1. Query active pairs by exam
        List<SimilarityPair> activeByExam = similarityPairRepository.findByExam_IdAndIsActiveTrue(exam.getId());
        assertThat(activeByExam).hasSize(1);
        assertThat(activeByExam.get(0).getId()).isEqualTo(pairId);

        // 2. Query active pairs by teacher
        List<SimilarityPair> activeByTeacher = similarityPairRepository.findActiveByTeacherId(teacher.getId());
        assertThat(activeByTeacher).extracting(SimilarityPair::getId).contains(pairId);

        // 3. Targeted metric update - updates rate/hashes/flagged, preserves adjudication fields
        int updated = similarityPairRepository.updateCalculatedMetrics(
                pairId, new BigDecimal("92.50"), 30, true
        );
        assertThat(updated).isEqualTo(1);

        entityManager.clear();

        SimilarityPair updatedPair = similarityPairRepository.findById(pairId).orElseThrow();
        assertThat(updatedPair.getSimilarityRate()).isEqualByComparingTo("92.50");
        assertThat(updatedPair.getSharedHashesCount()).isEqualTo(30);
        assertThat(updatedPair.getIsFlagged()).isTrue();
        assertThat(updatedPair.getIsActive()).isTrue();
        // Crucial invariant: Adjudication fields survived intact
        assertThat(updatedPair.getAdjudicationStatus()).isEqualTo("FLAG_CONFIRMED");
        assertThat(updatedPair.getAdjudicationNotes()).isEqualTo("Verified identical loops");
        assertThat(updatedPair.getAdjudicatedBy().getId()).isEqualTo(teacher.getId());

        // 4. Soft deactivation
        int deactivated = similarityPairRepository.deactivatePairsByIds(Collections.singletonList(pairId));
        assertThat(deactivated).isEqualTo(1);

        entityManager.clear();

        SimilarityPair deactivatedPair = similarityPairRepository.findById(pairId).orElseThrow();
        assertThat(deactivatedPair.getIsActive()).isFalse();

        // 5. Excluded from active teacher view, but present in full audit view
        List<SimilarityPair> activeAfterDeactivation = similarityPairRepository.findActiveByTeacherId(teacher.getId());
        assertThat(activeAfterDeactivation).extracting(SimilarityPair::getId).doesNotContain(pairId);

        List<SimilarityPair> allAfterDeactivation = similarityPairRepository.findAllByTeacherId(teacher.getId());
        assertThat(allAfterDeactivation).extracting(SimilarityPair::getId).contains(pairId);
    }
}
