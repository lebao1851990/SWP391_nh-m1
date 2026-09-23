package com.codeassess.plagiarism.service;

import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.normalizer.AstNormalizer;
import com.codeassess.plagiarism.parser.JavaAstParser;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.tokenizer.KGramGenerator;
import com.codeassess.plagiarism.winnowing.FingerprintHasher;
import com.codeassess.plagiarism.winnowing.Winnower;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

class AstPlagiarismServiceTest {

    private SubmissionRepository submissionRepository;
    private AstFingerprintRepository fingerprintRepository;
    private SimilarityPairRepository similarityPairRepository;
    private PlagiarismEvidenceRepository evidenceRepository;
    private AstPlagiarismService service;

    @BeforeEach
    void setUp() {
        submissionRepository = Mockito.mock(SubmissionRepository.class);
        fingerprintRepository = Mockito.mock(AstFingerprintRepository.class);
        similarityPairRepository = Mockito.mock(SimilarityPairRepository.class);
        evidenceRepository = Mockito.mock(PlagiarismEvidenceRepository.class);
        
        JavaAstParser parser = new JavaAstParser();
        AstNormalizer normalizer = new AstNormalizer();
        KGramGenerator kGramGenerator = new KGramGenerator(5);
        FingerprintHasher hasher = new FingerprintHasher();
        Winnower winnower = new Winnower(4);

        service = new AstPlagiarismService(submissionRepository, fingerprintRepository, similarityPairRepository, evidenceRepository, parser, normalizer, kGramGenerator, hasher, winnower);
    }

    @Test
    void testEndToEndPipelineDeterminismAndPersistence() {
        String code = "class A { int calc(int a) { return a * 2; } }";
        Submission submission = Submission.builder().id("sub-1").sourceCode(code).build();

        Mockito.when(submissionRepository.findById("sub-1")).thenReturn(Optional.of(submission));

        service.generateFingerprintsForSubmission("sub-1");

        // Verify old fingerprints were deleted
        verify(fingerprintRepository).deleteBySubmission_Id("sub-1");

        // Capture saved fingerprints
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AstFingerprint>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fingerprintRepository).saveAll(captor.capture());

        List<AstFingerprint> savedFingerprints = captor.getValue();
        
        // Assert we actually generated some fingerprints
        assertTrue(savedFingerprints.size() > 0);
        
        // Ensure metadata is filled correctly
        AstFingerprint first = savedFingerprints.get(0);
        assertEquals("sub-1", first.getSubmission().getId());
        assertTrue(first.getHashValue() != 0L); // hash is generated
        assertTrue(first.getStartLine() > 0);
        assertTrue(first.getEndLine() > 0);
        
        // Run again with SAME code to ensure determinism
        service.generateFingerprintsForSubmission("sub-1");
        
        ArgumentCaptor<List<AstFingerprint>> captor2 = ArgumentCaptor.forClass((Class) List.class);
        verify(fingerprintRepository, Mockito.times(2)).saveAll(captor2.capture());
        
        List<AstFingerprint> savedFingerprints2 = captor2.getValue();
        
        // Both lists must have identical hashes and metadata
        assertEquals(savedFingerprints.size(), savedFingerprints2.size());
        for (int i = 0; i < savedFingerprints.size(); i++) {
            assertEquals(savedFingerprints.get(i).getHashValue(), savedFingerprints2.get(i).getHashValue());
            assertEquals(savedFingerprints.get(i).getNodeType(), savedFingerprints2.get(i).getNodeType());
            assertEquals(savedFingerprints.get(i).getStartLine(), savedFingerprints2.get(i).getStartLine());
            assertEquals(savedFingerprints.get(i).getEndLine(), savedFingerprints2.get(i).getEndLine());
        }
    }
}
