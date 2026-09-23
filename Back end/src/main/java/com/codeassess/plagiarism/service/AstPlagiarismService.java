package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.normalizer.AstNormalizer;
import com.codeassess.plagiarism.parser.AstNode;
import com.codeassess.plagiarism.parser.JavaAstParser;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.tokenizer.KGram;
import com.codeassess.plagiarism.tokenizer.KGramGenerator;
import com.codeassess.plagiarism.winnowing.FingerprintHasher;
import com.codeassess.plagiarism.winnowing.HashedKGram;
import com.codeassess.plagiarism.winnowing.Winnower;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.github.javaparser.ast.CompilationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AstPlagiarismService {

    private final SubmissionRepository submissionRepository;
    private final AstFingerprintRepository astFingerprintRepository;
    private final SimilarityPairRepository similarityPairRepository;
    private final PlagiarismEvidenceRepository plagiarismEvidenceRepository;
    private final JavaAstParser parser;
    private final AstNormalizer normalizer;
    private final KGramGenerator kGramGenerator;
    private final FingerprintHasher hasher;
    private final Winnower winnower;

    @Transactional
    public void generateFingerprintsForSubmission(String submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        if (submission.getSourceCode() == null || submission.getSourceCode().trim().isEmpty()) {
            return;
        }

        // 1. Delete associated evidence and similarity_matrix rows to prevent stale data
        List<SimilarityPair> stalePairs = similarityPairRepository.findBySubmissionA_IdOrSubmissionB_Id(submissionId, submissionId);
        for (SimilarityPair pair : stalePairs) {
            plagiarismEvidenceRepository.deleteBySimilarityPair_Id(pair.getId());
        }
        similarityPairRepository.deleteAll(stalePairs);

        // 2. Delete existing fingerprints to ensure no stale data
        astFingerprintRepository.deleteBySubmission_Id(submissionId);

        // 1. Parse
        CompilationUnit cu = parser.parse(submission.getSourceCode());

        // 2. Normalize
        List<AstNode> normalizedSequence = normalizer.normalize(cu);

        // 3. Generate K-Grams
        List<KGram> kGrams = kGramGenerator.generateKGrams(normalizedSequence);

        // 4. Hash K-Grams
        List<HashedKGram> hashedKGrams = new ArrayList<>();
        for (int i = 0; i < kGrams.size(); i++) {
            KGram kg = kGrams.get(i);
            long hashValue = hasher.hash(kg.getConcatenatedTypeString());
            hashedKGrams.add(new HashedKGram(hashValue, i, kg.getRepresentativeNodeType(), kg.getStartLine(), kg.getEndLine()));
        }

        // 5. Winnow
        List<HashedKGram> winnowedHashes = winnower.winnow(hashedKGrams);

        // 6. Persist
        List<AstFingerprint> fingerprints = new ArrayList<>();
        for (HashedKGram hk : winnowedHashes) {
            fingerprints.add(AstFingerprint.builder()
                    .submission(submission)
                    .hashValue(hk.getHash())
                    .nodeType(hk.getNodeType())
                    .startLine(hk.getStartLine())
                    .endLine(hk.getEndLine())
                    .build());
        }

        astFingerprintRepository.saveAll(fingerprints);
    }
}
