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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AstPlagiarismService {

    private final SubmissionRepository submissionRepository;
    private final AstFingerprintRepository astFingerprintRepository;
    private final JavaAstParser parser;
    private final AstNormalizer normalizer;
    private final KGramGenerator kGramGenerator;
    private final FingerprintHasher hasher;
    private final Winnower winnower;

    @Transactional
    public void generateFingerprintsForSubmission(String submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        if ("ZIP".equalsIgnoreCase(submission.getSubmissionType())) {
            log.info("Submission {} is a ZIP submission; skipping single-file fingerprint generation", submissionId);
            return;
        }

        if (submission.getSourceCode() == null || submission.getSourceCode().trim().isEmpty()) {
            return;
        }

        // Delete existing fingerprints to ensure no stale data
        astFingerprintRepository.deleteBySubmission_Id(submissionId);

        // 1. Parse
        CompilationUnit cu;
        try {
            cu = parser.parse(submission.getSourceCode());
        } catch (Exception e) {
            log.warn("Cannot parse source code for submission {} (compilation/syntax error): {}", submissionId, e.getMessage());
            return;
        }

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
                    .filePath("Main.java")
                    .build());
        }

        astFingerprintRepository.saveAll(fingerprints);
    }

    @Transactional
    public void generateFingerprintsForWorkspace(Submission submission, com.codeassess.submission.dto.ExtractedWorkspace workspace) {
        if (submission == null || workspace == null || workspace.files() == null || workspace.files().isEmpty()) {
            return;
        }

        String submissionId = submission.getId();

        // Delete existing fingerprints
        astFingerprintRepository.deleteBySubmission_Id(submissionId);

        List<AstFingerprint> allFingerprints = new ArrayList<>();

        // Process Java files sorted deterministically by relative path
        List<com.codeassess.submission.dto.ExtractedFile> javaFiles = workspace.files().stream()
                .filter(com.codeassess.submission.dto.ExtractedFile::isJavaSource)
                .sorted(java.util.Comparator.comparing(com.codeassess.submission.dto.ExtractedFile::relativePath))
                .toList();

        for (com.codeassess.submission.dto.ExtractedFile file : javaFiles) {
            try {
                String sourceCode = java.nio.file.Files.readString(file.absolutePath(), java.nio.charset.StandardCharsets.UTF_8);
                if (sourceCode.trim().isEmpty()) {
                    continue;
                }

                CompilationUnit cu = parser.parse(sourceCode);
                List<AstNode> normalizedSequence = normalizer.normalize(cu);
                List<KGram> kGrams = kGramGenerator.generateKGrams(normalizedSequence);

                List<HashedKGram> hashedKGrams = new ArrayList<>();
                for (int i = 0; i < kGrams.size(); i++) {
                    KGram kg = kGrams.get(i);
                    long hashValue = hasher.hash(kg.getConcatenatedTypeString());
                    hashedKGrams.add(new HashedKGram(hashValue, i, kg.getRepresentativeNodeType(), kg.getStartLine(), kg.getEndLine()));
                }

                List<HashedKGram> winnowedHashes = winnower.winnow(hashedKGrams);
                for (HashedKGram hk : winnowedHashes) {
                    allFingerprints.add(AstFingerprint.builder()
                            .submission(submission)
                            .hashValue(hk.getHash())
                            .nodeType(hk.getNodeType())
                            .startLine(hk.getStartLine())
                            .endLine(hk.getEndLine())
                            .filePath(file.relativePath())
                            .build());
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AstPlagiarismService.class)
                        .warn("Could not parse AST for file {} in submission {}: {}", file.relativePath(), submissionId, e.getMessage());
            }
        }

        if (!allFingerprints.isEmpty()) {
            astFingerprintRepository.saveAll(allFingerprints);
        }
    }
}
