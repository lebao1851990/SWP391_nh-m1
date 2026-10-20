package com.codeassess.submission.service;

import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.ExtractedWorkspace;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceHasherServiceTest {

    private SourceHasherService hasherService;

    @BeforeEach
    void setUp() {
        hasherService = new SourceHasherService();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Archive SHA-256 Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Calculates exact SHA-256 hex string for byte array and stream")
    void archiveSha256_matchesKnownHash() throws IOException {
        byte[] input = "CodeAssess Milestone 2 Test Content".getBytes(StandardCharsets.UTF_8);

        String hashFromBytes = hasherService.calculateArchiveSha256(input);
        String hashFromStream = hasherService.calculateArchiveSha256(new ByteArrayInputStream(input));

        assertThat(hashFromBytes)
                .isEqualTo(hashFromStream)
                .hasSize(64)
                .matches("^[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("Archive SHA-256 rejects null inputs")
    void archiveSha256_rejectsNull() {
        assertThatThrownBy(() -> hasherService.calculateArchiveSha256((byte[]) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Deterministic Source Tree Hash Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Equivalent source code with CRLF vs LF produces identical tree hash")
    void sourceTreeHash_normalizesLineEndings() {
        String unixCode = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(1);\n    }\n}\n";
        String windowsCode = "public class Main {\r\n    public static void main(String[] args) {\r\n        System.out.println(1);\r\n    }\r\n}\r\n";

        String hashUnix = hasherService.calculateSourceTreeSha256(Map.of("src/Main.java", unixCode));
        String hashWindows = hasherService.calculateSourceTreeSha256(Map.of("src/Main.java", windowsCode));

        assertThat(hashUnix).isEqualTo(hashWindows);
    }

    @Test
    @DisplayName("Equivalent source code with trailing line whitespace produces identical tree hash")
    void sourceTreeHash_normalizesTrailingWhitespace() {
        String cleanCode = "public class Main {\n    int a = 10;\n}\n";
        String spaceCode = "public class Main {   \n    int a = 10; \t \n}\n   \n";

        String hashClean = hasherService.calculateSourceTreeSha256(Map.of("src/Main.java", cleanCode));
        String hashSpace = hasherService.calculateSourceTreeSha256(Map.of("src/Main.java", spaceCode));

        assertThat(hashClean).isEqualTo(hashSpace);
    }

    @Test
    @DisplayName("File insertion order in map or archive does not affect tree hash")
    void sourceTreeHash_independentOfFileOrder() {
        Map<String, String> order1 = new LinkedHashMap<>();
        order1.put("src/model/User.java", "package model; public class User {}");
        order1.put("src/Main.java", "public class Main {}");

        Map<String, String> order2 = new LinkedHashMap<>();
        order2.put("src/Main.java", "public class Main {}");
        order2.put("src/model/User.java", "package model; public class User {}");

        String hash1 = hasherService.calculateSourceTreeSha256(order1);
        String hash2 = hasherService.calculateSourceTreeSha256(order2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Path separators backslash vs slash produce identical tree hash")
    void sourceTreeHash_normalizesPathSeparators() {
        String code = "public class User {}";

        String hashSlash = hasherService.calculateSourceTreeSha256(Map.of("src/model/User.java", code));
        String hashBackslash = hasherService.calculateSourceTreeSha256(Map.of("src\\model\\User.java", code));

        assertThat(hashSlash).isEqualTo(hashBackslash);
    }

    @Test
    @DisplayName("Meaningful source code changes produce a different tree hash")
    void sourceTreeHash_detectsContentChanges() {
        String original = "public class Main { int x = 1; }";
        String modified = "public class Main { int x = 2; }";

        String hashOriginal = hasherService.calculateSourceTreeSha256(Map.of("src/Main.java", original));
        String hashModified = hasherService.calculateSourceTreeSha256(Map.of("src/Main.java", modified));

        assertThat(hashOriginal).isNotEqualTo(hashModified);
    }

    @Test
    @DisplayName("Length-prefixed framing prevents cross-boundary file collisions")
    void sourceTreeHash_framingPreventsBoundaryCollision() {
        // Path "a" with content "bc" vs path "ab" with content "c"
        String hash1 = hasherService.calculateSourceTreeSha256(Map.of("a.java", "bc"));
        String hash2 = hasherService.calculateSourceTreeSha256(Map.of("ab.java", "c"));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Dual Hash Invariance & Non-Conflation Test
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Two different ZIP archives of identical source code have different archiveSha256 but identical sourceTreeSha256")
    void dualHash_distinctAndInvariant() throws IOException {
        ZipExtractionService extractionService = new ZipExtractionService(new ZipSubmissionProperties());

        String sourceCode = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}\n";

        // Archive 1: Created with timestamp T1
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(baos1)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("src/Main.java");
            entry.setTime(1_000_000_000L); // Timestamp T1
            zaos.putArchiveEntry(entry);
            zaos.write(sourceCode.getBytes(StandardCharsets.UTF_8));
            zaos.closeArchiveEntry();
        }
        byte[] zip1 = baos1.toByteArray();

        // Archive 2: Same source file, but different entry timestamp and extra comment
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(baos2)) {
            zaos.setComment("Different archive metadata comment");
            ZipArchiveEntry entry = new ZipArchiveEntry("src/Main.java");
            entry.setTime(2_000_000_000L); // Timestamp T2
            zaos.putArchiveEntry(entry);
            zaos.write(sourceCode.getBytes(StandardCharsets.UTF_8));
            zaos.closeArchiveEntry();
        }
        byte[] zip2 = baos2.toByteArray();

        // Archive SHA-256 must be different because compressed bytes differ
        String archiveHash1 = hasherService.calculateArchiveSha256(zip1);
        String archiveHash2 = hasherService.calculateArchiveSha256(zip2);
        assertThat(archiveHash1).isNotEqualTo(archiveHash2);

        // Source tree hashes computed from extracted workspaces must be 100% IDENTICAL
        String treeHash1;
        try (ExtractedWorkspace w1 = extractionService.extract(zip1)) {
            treeHash1 = hasherService.calculateSourceTreeSha256(w1.files());
        }

        String treeHash2;
        try (ExtractedWorkspace w2 = extractionService.extract(zip2)) {
            treeHash2 = hasherService.calculateSourceTreeSha256(w2.files());
        }

        assertThat(treeHash1)
                .isEqualTo(treeHash2)
                .matches("^[a-f0-9]{64}$");
    }
}
