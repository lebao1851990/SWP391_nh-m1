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

        service = new AstPlagiarismService(submissionRepository, fingerprintRepository, parser, normalizer, kGramGenerator, hasher, winnower);
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

    @Test
    void testWorkspaceMultiFileFingerprintGenerationAndRelativePaths() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("ast-test-ws-");
        java.nio.file.Path f1 = tempDir.resolve("Main.java");
        java.nio.file.Files.writeString(f1, "public class Main { public static void main(String[] args) { System.out.println(1); } }");
        java.nio.file.Path subDir = tempDir.resolve("util");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path f2 = subDir.resolve("Helper.java");
        java.nio.file.Files.writeString(f2, "package util; public class Helper { public int add(int a, int b) { return a + b; } }");

        com.codeassess.submission.dto.ExtractedWorkspace workspace = new com.codeassess.submission.dto.ExtractedWorkspace(
                tempDir,
                List.of(
                        new com.codeassess.submission.dto.ExtractedFile("Main.java", f1, java.nio.file.Files.size(f1), true),
                        new com.codeassess.submission.dto.ExtractedFile("util/Helper.java", f2, java.nio.file.Files.size(f2), true)
                ),
                1024L,
                "dummy-sha"
        );

        Submission submission = Submission.builder().id("sub-ws-1").build();

        service.generateFingerprintsForWorkspace(submission, workspace);

        verify(fingerprintRepository).deleteBySubmission_Id("sub-ws-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AstFingerprint>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fingerprintRepository).saveAll(captor.capture());

        List<AstFingerprint> saved = captor.getValue();
        assertTrue(saved.size() > 0);

        // Verify that relative paths from both files are present
        boolean hasMain = saved.stream().anyMatch(fp -> "Main.java".equals(fp.getFilePath()));
        boolean hasHelper = saved.stream().anyMatch(fp -> "util/Helper.java".equals(fp.getFilePath()));
        assertTrue(hasMain, "Fingerprints should include Main.java");
        assertTrue(hasHelper, "Fingerprints should include util/Helper.java");

        for (AstFingerprint fp : saved) {
            assertEquals("sub-ws-1", fp.getSubmission().getId());
            assertTrue(fp.getStartLine() > 0);
            assertTrue(fp.getEndLine() >= fp.getStartLine());
        }

        workspace.close();
    }

    @Test
    void testWorkspaceDeterministicFileProcessingOrder() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("ast-test-ws-order-");
        java.nio.file.Path fA = tempDir.resolve("Alpha.java");
        java.nio.file.Files.writeString(fA, "public class Alpha { void a() { int x = 1; } }");
        java.nio.file.Path fB = tempDir.resolve("Beta.java");
        java.nio.file.Files.writeString(fB, "public class Beta { void b() { int y = 2; } }");

        // Order 1: Alpha then Beta
        com.codeassess.submission.dto.ExtractedWorkspace ws1 = new com.codeassess.submission.dto.ExtractedWorkspace(
                tempDir,
                List.of(
                        new com.codeassess.submission.dto.ExtractedFile("Alpha.java", fA, java.nio.file.Files.size(fA), true),
                        new com.codeassess.submission.dto.ExtractedFile("Beta.java", fB, java.nio.file.Files.size(fB), true)
                ),
                512L, "sha"
        );

        // Order 2: Beta then Alpha (reversed)
        com.codeassess.submission.dto.ExtractedWorkspace ws2 = new com.codeassess.submission.dto.ExtractedWorkspace(
                tempDir,
                List.of(
                        new com.codeassess.submission.dto.ExtractedFile("Beta.java", fB, java.nio.file.Files.size(fB), true),
                        new com.codeassess.submission.dto.ExtractedFile("Alpha.java", fA, java.nio.file.Files.size(fA), true)
                ),
                512L, "sha"
        );

        Submission sub1 = Submission.builder().id("sub-order-1").build();
        Submission sub2 = Submission.builder().id("sub-order-2").build();

        service.generateFingerprintsForWorkspace(sub1, ws1);
        service.generateFingerprintsForWorkspace(sub2, ws2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AstFingerprint>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fingerprintRepository, Mockito.times(2)).saveAll(captor.capture());

        List<List<AstFingerprint>> allBatches = captor.getAllValues();
        List<AstFingerprint> batch1 = allBatches.get(0);
        List<AstFingerprint> batch2 = allBatches.get(1);

        assertEquals(batch1.size(), batch2.size());
        for (int i = 0; i < batch1.size(); i++) {
            assertEquals(batch1.get(i).getFilePath(), batch2.get(i).getFilePath());
            assertEquals(batch1.get(i).getHashValue(), batch2.get(i).getHashValue());
        }

        ws1.close();
        ws2.close();
    }

    @Test
    void testWorkspaceMalformedJavaFileAlongsideValidFile() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("ast-test-ws-malformed-");
        java.nio.file.Path fValid = tempDir.resolve("Good.java");
        java.nio.file.Files.writeString(fValid, "public class Good { void run() { int sum = 0; for(int i=0; i<10; i++) sum += i; } }");
        java.nio.file.Path fBroken = tempDir.resolve("Broken.java");
        java.nio.file.Files.writeString(fBroken, "class Broken { void unclosed( { invalid syntax here !!!");

        com.codeassess.submission.dto.ExtractedWorkspace workspace = new com.codeassess.submission.dto.ExtractedWorkspace(
                tempDir,
                List.of(
                        new com.codeassess.submission.dto.ExtractedFile("Broken.java", fBroken, java.nio.file.Files.size(fBroken), true),
                        new com.codeassess.submission.dto.ExtractedFile("Good.java", fValid, java.nio.file.Files.size(fValid), true)
                ),
                512L, "sha"
        );

        Submission submission = Submission.builder().id("sub-broken-1").build();

        // Must not throw an unhandled exception
        service.generateFingerprintsForWorkspace(submission, workspace);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AstFingerprint>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fingerprintRepository).saveAll(captor.capture());

        List<AstFingerprint> saved = captor.getValue();
        // Good.java must still have fingerprints generated
        assertTrue(saved.size() > 0);
        assertTrue(saved.stream().allMatch(fp -> "Good.java".equals(fp.getFilePath())));

        workspace.close();
    }

    @Test
    void testWorkspaceEmptyOrNoJavaFilesDoesNothingGracefully() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("ast-test-ws-empty-");
        java.nio.file.Path fText = tempDir.resolve("README.md");
        java.nio.file.Files.writeString(fText, "# Just documentation");

        com.codeassess.submission.dto.ExtractedWorkspace wsNoJava = new com.codeassess.submission.dto.ExtractedWorkspace(
                tempDir,
                List.of(new com.codeassess.submission.dto.ExtractedFile("README.md", fText, java.nio.file.Files.size(fText), false)),
                100L, "sha"
        );

        Submission sub = Submission.builder().id("sub-empty-1").build();
        service.generateFingerprintsForWorkspace(sub, wsNoJava);

        // No fingerprints saved
        verify(fingerprintRepository, Mockito.never()).saveAll(Mockito.anyList());

        // Null workspace or null submission
        service.generateFingerprintsForWorkspace(null, wsNoJava);
        service.generateFingerprintsForWorkspace(sub, null);

        wsNoJava.close();
    }
}
