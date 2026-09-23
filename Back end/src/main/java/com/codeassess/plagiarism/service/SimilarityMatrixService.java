package com.codeassess.plagiarism.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.plagiarism.similarity.SimilarityResult;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimilarityMatrixService {

    private final ExamRepository examRepository;
    private final SubmissionRepository submissionRepository;
    private final AstFingerprintRepository fingerprintRepository;
    private final SimilarityPairRepository similarityPairRepository;
    private final PlagiarismEvidenceRepository evidenceRepository;
    private final JaccardSimilarityCalculator similarityCalculator;
    private final EvidenceGenerator evidenceGenerator;

    @Transactional
    public void generateMatrixForExam(String examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found"));

        List<Submission> submissions = submissionRepository.findByExam_Id(examId);

        // Delete all existing matrix and evidence for this exam to ensure freshness
        List<SimilarityPair> existingPairs = similarityPairRepository.findByExam_Id(examId);
        for (SimilarityPair pair : existingPairs) {
            evidenceRepository.deleteBySimilarityPair_Id(pair.getId());
        }
        similarityPairRepository.deleteAll(existingPairs);
        similarityPairRepository.flush(); // Force Hibernate to execute DELETEs before the upcoming INSERTs

        // Load all fingerprints for the exam submissions
        Map<String, List<AstFingerprint>> submissionFingerprints = new HashMap<>();
        for (Submission sub : submissions) {
            submissionFingerprints.put(sub.getId(), fingerprintRepository.findBySubmission_Id(sub.getId()));
        }

        List<SimilarityPair> pairsToSave = new ArrayList<>();
        List<List<PlagiarismEvidence>> evidencesToSave = new ArrayList<>();

        for (int i = 0; i < submissions.size(); i++) {
            for (int j = i + 1; j < submissions.size(); j++) {
                Submission sub1 = submissions.get(i);
                Submission sub2 = submissions.get(j);

                Submission subA = sub1.getId().compareTo(sub2.getId()) < 0 ? sub1 : sub2;
                Submission subB = sub1.getId().compareTo(sub2.getId()) < 0 ? sub2 : sub1;

                List<AstFingerprint> fpA = submissionFingerprints.get(subA.getId());
                List<AstFingerprint> fpB = submissionFingerprints.get(subB.getId());

                Set<Long> hashesA = fpA.stream().map(AstFingerprint::getHashValue).collect(Collectors.toSet());
                Set<Long> hashesB = fpB.stream().map(AstFingerprint::getHashValue).collect(Collectors.toSet());

                SimilarityResult result = similarityCalculator.calculate(hashesA, hashesB);

                SimilarityPair pair = SimilarityPair.builder()
                        .exam(exam)
                        .submissionA(subA)
                        .submissionB(subB)
                        .similarityRate(result.getSimilarityRate())
                        .sharedHashesCount(result.getSharedHashesCount())
                        .isFlagged(result.isFlagged())
                        .build();

                // Save pair first to generate ID, then generate evidence
                pair = similarityPairRepository.save(pair);

                if (result.getSharedHashesCount() > 0) {
                    List<PlagiarismEvidence> evidences = evidenceGenerator.generateEvidence(pair, fpA, fpB);
                    evidenceRepository.saveAll(evidences);
                }
            }
        }
    }
}
