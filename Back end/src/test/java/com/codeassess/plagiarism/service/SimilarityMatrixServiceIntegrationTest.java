package com.codeassess.plagiarism.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codeassess.user.entity.Role;

import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SimilarityMatrixServiceIntegrationTest {

    @Autowired
    private SimilarityMatrixService similarityMatrixService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseClassRepository courseClassRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SimilarityPairRepository similarityPairRepository;

    @Autowired
    private PlagiarismEvidenceRepository plagiarismEvidenceRepository;

    @Test
    public void testGenerateMatrixWithExistingPairsDoesNotThrowConstraintViolation() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        // Setup Users
        User teacher = userRepository.save(User.builder().email("tc_" + uniqueSuffix + "@test.com").fullName("tc").passwordHash("pwd").role(Role.TEACHER).build());
        User stuA = userRepository.save(User.builder().email("sa_" + uniqueSuffix + "@test.com").fullName("sa").passwordHash("pwd").role(Role.STUDENT).build());
        User stuB = userRepository.save(User.builder().email("sb_" + uniqueSuffix + "@test.com").fullName("sb").passwordHash("pwd").role(Role.STUDENT).build());
        User stuC = userRepository.save(User.builder().email("sc_" + uniqueSuffix + "@test.com").fullName("sc").passwordHash("pwd").role(Role.STUDENT).build());

        // Setup Class & Exam
        CourseClass courseClass = courseClassRepository.save(CourseClass.builder().name("Class 1").code("C1").semester("FA24").teacher(teacher).build());
        Exam exam = examRepository.save(Exam.builder().title("Exam 1").status("ACTIVE").courseClass(courseClass).build());

        // Setup 3 Submissions
        Submission subA = submissionRepository.save(Submission.builder().exam(exam).student(stuA).status("PENDING").sourceCode("code A").build());
        Submission subB = submissionRepository.save(Submission.builder().exam(exam).student(stuB).status("PENDING").sourceCode("code B").build());
        Submission subC = submissionRepository.save(Submission.builder().exam(exam).student(stuC).status("PENDING").sourceCode("code C").build());

        Submission sub1 = subA.getId().compareTo(subB.getId()) < 0 ? subA : subB;
        Submission sub2 = subA.getId().compareTo(subB.getId()) < 0 ? subB : subA;
        SimilarityPair pair1 = similarityPairRepository.save(SimilarityPair.builder().exam(exam).submissionA(sub1).submissionB(sub2).similarityRate(BigDecimal.TEN).isFlagged(false).sharedHashesCount(10).build());

        Submission sub3 = subA.getId().compareTo(subC.getId()) < 0 ? subA : subC;
        Submission sub4 = subA.getId().compareTo(subC.getId()) < 0 ? subC : subA;
        SimilarityPair pair2 = similarityPairRepository.save(SimilarityPair.builder().exam(exam).submissionA(sub3).submissionB(sub4).similarityRate(BigDecimal.TEN).isFlagged(false).sharedHashesCount(10).build());

        Submission sub5 = subB.getId().compareTo(subC.getId()) < 0 ? subB : subC;
        Submission sub6 = subB.getId().compareTo(subC.getId()) < 0 ? subC : subB;
        SimilarityPair pair3 = similarityPairRepository.save(SimilarityPair.builder().exam(exam).submissionA(sub5).submissionB(sub6).similarityRate(BigDecimal.TEN).isFlagged(false).sharedHashesCount(10).build());

        assertEquals(3, similarityPairRepository.findByExam_Id(exam.getId()).size(), "Initial pairs should be 3");

        // Act: Trigger regeneration
        // If the flush is missing, this will throw SQLIntegrityConstraintViolationException (or DataIntegrityViolationException in Spring)
        similarityMatrixService.generateMatrixForExam(exam.getId());

        // Assert
        List<SimilarityPair> finalPairs = similarityPairRepository.findByExam_Id(exam.getId());
        
        // 3 submissions -> 3*(3-1)/2 = 3 pairs
        assertEquals(3, finalPairs.size(), "Matrix should have exactly 3 pairs");

        // Verify correct ordering and no duplicates
        boolean hasAB = false, hasAC = false, hasBC = false;
        for (SimilarityPair pair : finalPairs) {
            assertTrue(pair.getSubmissionA().getId().compareTo(pair.getSubmissionB().getId()) < 0, "Submission A must be ordered before Submission B");
            if ((pair.getSubmissionA().getId().equals(subA.getId()) && pair.getSubmissionB().getId().equals(subB.getId())) ||
                (pair.getSubmissionA().getId().equals(subB.getId()) && pair.getSubmissionB().getId().equals(subA.getId()))) hasAB = true;
            if ((pair.getSubmissionA().getId().equals(subA.getId()) && pair.getSubmissionB().getId().equals(subC.getId())) ||
                (pair.getSubmissionA().getId().equals(subC.getId()) && pair.getSubmissionB().getId().equals(subA.getId()))) hasAC = true;
            if ((pair.getSubmissionA().getId().equals(subB.getId()) && pair.getSubmissionB().getId().equals(subC.getId())) ||
                (pair.getSubmissionA().getId().equals(subC.getId()) && pair.getSubmissionB().getId().equals(subB.getId()))) hasBC = true;
        }

        assertTrue(hasAB, "Missing pair A-B");
        assertTrue(hasAC, "Missing pair A-C");
        assertTrue(hasBC, "Missing pair B-C");
    }
}
