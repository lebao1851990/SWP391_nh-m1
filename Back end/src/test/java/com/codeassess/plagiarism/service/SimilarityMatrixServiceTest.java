package com.codeassess.plagiarism.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SimilarityMatrixServiceTest {

    private ExamRepository examRepository;
    private SubmissionRepository submissionRepository;
    private AstFingerprintRepository fingerprintRepository;
    private SimilarityPairRepository similarityPairRepository;
    private PlagiarismEvidenceRepository evidenceRepository;
    private JaccardSimilarityCalculator similarityCalculator;
    private EvidenceGenerator evidenceGenerator;
    private SimilarityMatrixService service;

    @BeforeEach
    void setUp() {
        examRepository = mock(ExamRepository.class);
        submissionRepository = mock(SubmissionRepository.class);
        fingerprintRepository = mock(AstFingerprintRepository.class);
        similarityPairRepository = mock(SimilarityPairRepository.class);
        evidenceRepository = mock(PlagiarismEvidenceRepository.class);
        similarityCalculator = new JaccardSimilarityCalculator();
        evidenceGenerator = new EvidenceGenerator();

        service = new SimilarityMatrixService(examRepository, submissionRepository, fingerprintRepository,
                similarityPairRepository, evidenceRepository, similarityCalculator, evidenceGenerator);
    }

    @Test
    void testGenerateMatrixNormalizesOrderingAndRejectsSelf() {
        Exam exam = Exam.builder().id("exam-1").build();
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(exam));

        // Submissions with IDs out of order
        Submission subZ = Submission.builder().id("sub-Z").build();
        Submission subA = Submission.builder().id("sub-A").build();
        Submission subM = Submission.builder().id("sub-M").build();

        when(submissionRepository.findByExam_Id("exam-1")).thenReturn(Arrays.asList(subZ, subA, subM));
        when(similarityPairRepository.findByExam_Id("exam-1")).thenReturn(Collections.emptyList());
        when(fingerprintRepository.findBySubmission_Id(anyString())).thenReturn(Collections.emptyList());
        when(similarityPairRepository.save(any(SimilarityPair.class))).thenAnswer(i -> i.getArguments()[0]);

        service.generateMatrixForExam("exam-1");

        ArgumentCaptor<SimilarityPair> captor = ArgumentCaptor.forClass(SimilarityPair.class);
        verify(similarityPairRepository, times(3)).save(captor.capture());

        List<SimilarityPair> savedPairs = captor.getAllValues();
        assertEquals(3, savedPairs.size());

        // A/Z, A/M, M/Z - normalized means first string is lexicographically smaller
        for (SimilarityPair pair : savedPairs) {
            String id1 = pair.getSubmissionA().getId();
            String id2 = pair.getSubmissionB().getId();
            assertTrue(id1.compareTo(id2) < 0, "Pair ordering is not normalized: " + id1 + " >= " + id2);
        }
    }
}
