package com.codeassess.submission.service;

import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.exception.ZipValidationException;
import com.codeassess.submission.exception.ZipValidationException.ErrorCode;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipExtractionServiceTest {

    private ZipSubmissionProperties properties;
    private ZipExtractionService service;

    @BeforeEach
    void setUp() {
        properties = new ZipSubmissionProperties();
        service = new ZipExtractionService(properties);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper to build test ZIPs in memory
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] createZip(EntryBuilder... builders) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(baos)) {
            for (EntryBuilder b : builders) {
                b.write(zaos);
            }
        }
        return baos.toByteArray();
    }

    @FunctionalInterface
    interface EntryBuilder {
        void write(ZipArchiveOutputStream zaos) throws IOException;
    }

    private EntryBuilder file(String name, String content) {
        return zaos -> {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            entry.setSize(bytes.length);
            zaos.putArchiveEntry(entry);
            zaos.write(bytes);
            zaos.closeArchiveEntry();
        };
    }

    private EntryBuilder fileWithBytes(String name, byte[] bytes) {
        return zaos -> {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            entry.setSize(bytes.length);
            zaos.putArchiveEntry(entry);
            zaos.write(bytes);
            zaos.closeArchiveEntry();
        };
    }

    private EntryBuilder symlink(String name, String target) {
        return zaos -> {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            entry.setUnixMode(UnixStat.LINK_FLAG | 0777);
            byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
            entry.setSize(bytes.length);
            zaos.putArchiveEntry(entry);
            zaos.write(bytes);
            zaos.closeArchiveEntry();
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Valid ZIP Archives
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Extracts valid single-file Java ZIP successfully")
    void validSingleJavaFile_succeeds() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main { public static void main(String[] args) {} }")
        );

        Path workspacePath;
        try (ExtractedWorkspace workspace = service.extract(zip)) {
            workspacePath = workspace.workspaceDir();
            assertThat(Files.exists(workspacePath)).isTrue();
            assertThat(workspace.files()).hasSize(1);
            assertThat(workspace.files().get(0).relativePath()).isEqualTo("src/Main.java");
            assertThat(workspace.files().get(0).isJavaSource()).isTrue();
            assertThat(workspace.archiveSha256()).isNotEmpty();
        }

        // AutoCloseable check: directory removed on close
        assertThat(Files.exists(workspacePath)).isFalse();
    }

    @Test
    @DisplayName("Extracts multi-file Java ZIP with permitted auxiliary files")
    void validMultiFileWithAuxiliary_succeeds() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                file("src/model/User.java", "package model; public class User {}"),
                file("data/input.json", "{\"key\": \"value\"}"),
                file("README.md", "# CodeAssess Assignment")
        );

        try (ExtractedWorkspace workspace = service.extract(zip)) {
            assertThat(workspace.files()).hasSize(4);
            long javaCount = workspace.files().stream().filter(f -> f.isJavaSource()).count();
            assertThat(javaCount).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Empty or Malformed Archives
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rejects null or 0-byte archive with EMPTY_ARCHIVE")
    void emptyArchive_throwsException() {
        assertThatThrownBy(() -> service.extract(new byte[0]))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPTY_ARCHIVE);
    }

    @Test
    @DisplayName("Rejects corrupted/random non-zip bytes with NOT_A_ZIP_ARCHIVE")
    void corruptedArchive_throwsException() {
        byte[] randomBytes = "This is not a zip archive file at all!".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.extract(randomBytes))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_A_ZIP_ARCHIVE);
    }

    @Test
    @DisplayName("Rejects ZIP with zero entries")
    void zipWithNoEntries_throwsException() throws IOException {
        byte[] emptyZip = createZip();
        assertThatThrownBy(() -> service.extract(emptyZip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPTY_ARCHIVE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Security: Traversal Attacks (Zip Slip)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rejects ZIP Slip entry with ../ traversal")
    void zipSlipTraversal_throwsException() throws IOException {
        byte[] zip = createZip(
                file("../../evil.java", "public class Evil {}")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ZIP_SLIP_ATTEMPT);
    }

    @Test
    @DisplayName("Rejects entry with null byte in name")
    void nullByteInName_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main\0.java", "public class Main {}")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ZIP_SLIP_ATTEMPT);
    }

    @Test
    @DisplayName("Rejects absolute path starting with /")
    void absolutePathUnix_throwsException() throws IOException {
        byte[] zip = createZip(
                file("/etc/passwd.java", "public class Passwd {}")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ABSOLUTE_PATH_DISALLOWED);
    }

    @Test
    @DisplayName("Rejects drive-prefixed path like C:test.java")
    void drivePrefixedPath_throwsException() throws IOException {
        byte[] zip = createZip(
                file("C:test.java", "public class Test {}")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ABSOLUTE_PATH_DISALLOWED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Duplicate & Case-Colliding Paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rejects duplicate identical relative paths")
    void duplicatePaths_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                file("src/Main.java", "public class Main { int duplicate; }")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_OR_COLLIDING_PATH);
    }

    @Test
    @DisplayName("Rejects case-colliding paths (Main.java vs main.java)")
    void caseCollidingPaths_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                file("src/main.java", "public class main {}")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_OR_COLLIDING_PATH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Symbolic Links
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rejects Unix symbolic links in archive")
    void symlinkEntry_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                symlink("src/Link.java", "/etc/hosts")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYMLINK_DISALLOWED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. File Extension & Source Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rejects archive with forbidden binary extension (.class)")
    void forbiddenExtensionClass_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                file("src/Main.class", "bytecode")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_FILE_TYPE);
    }

    @Test
    @DisplayName("Rejects archive with unpermitted extension (.exe)")
    void forbiddenExtensionExe_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                file("tools/run.exe", "binary")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_FILE_TYPE);
    }

    @Test
    @DisplayName("Rejects archive with unpermitted extension (.jar)")
    void forbiddenExtensionJar_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                file("lib/malicious.jar", "binary-jar-content")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_FILE_TYPE);
    }

    @Test
    @DisplayName("Rejects archive with unpermitted extension (.sh)")
    void forbiddenExtensionSh_throwsException() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main {}"),
                file("scripts/attack.sh", "#!/bin/sh\nrm -rf /")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_FILE_TYPE);
    }

    @Test
    @DisplayName("Rejects archive containing zero Java source files")
    void noJavaSourceFiles_throwsException() throws IOException {
        byte[] zip = createZip(
                file("data.json", "{\"key\":\"value\"}"),
                file("notes.txt", "some notes")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_JAVA_SOURCE_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Bounds & Limits: File Count, Individual Size, Cumulative Size, Ratio
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rejects archive exceeding maximum file count")
    void exceedsMaxFileCount_throwsException() throws IOException {
        properties.setMaxFileCount(3);

        byte[] zip = createZip(
                file("src/A.java", "public class A {}"),
                file("src/B.java", "public class B {}"),
                file("src/C.java", "public class C {}"),
                file("src/D.java", "public class D {}")
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEEDS_MAX_FILE_COUNT);
    }

    @Test
    @DisplayName("Rejects individual file exceeding max individual size limit")
    void exceedsMaxIndividualSize_throwsException() throws IOException {
        properties.setMaxIndividualFileBytes(100);

        byte[] largeContent = new byte[150];
        Arrays.fill(largeContent, (byte) 'A');

        byte[] zip = createZip(
                fileWithBytes("src/Big.java", largeContent)
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEEDS_MAX_FILE_SIZE);
    }

    @Test
    @DisplayName("Rejects archive exceeding cumulative extracted size limit")
    void exceedsCumulativeExtractedSize_throwsException() throws IOException {
        properties.setMaxExtractedBytes(200);

        byte[] content1 = new byte[120];
        byte[] content2 = new byte[120];
        Arrays.fill(content1, (byte) 'A');
        Arrays.fill(content2, (byte) 'B');

        byte[] zip = createZip(
                fileWithBytes("src/A.java", content1),
                fileWithBytes("src/B.java", content2)
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEEDS_MAX_EXTRACTED_SIZE);
    }

    @Test
    @DisplayName("Rejects zip bomb with excessive compression ratio")
    void exceedsCompressionRatio_throwsException() throws IOException {
        properties.setMaxCompressionRatio(5.0);
        properties.setRatioThresholdBytes(100); // Trigger check after 100 bytes

        // Highly compressible zeroes
        byte[] zeroes = new byte[100_000];
        Arrays.fill(zeroes, (byte) 0);

        byte[] zip = createZip(
                fileWithBytes("src/Bomb.java", zeroes)
        );

        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEEDS_COMPRESSION_RATIO);
    }

    private EntryBuilder directory(String name) {
        return zaos -> {
            String dirName = name.endsWith("/") ? name : name + "/";
            ZipArchiveEntry entry = new ZipArchiveEntry(dirName);
            zaos.putArchiveEntry(entry);
            zaos.closeArchiveEntry();
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Targeted Phase 2B Group 4 Test Extensions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Extracts valid archive from InputStream successfully")
    void extractFromInputStream_validArchive_succeeds() throws IOException {
        byte[] zip = createZip(
                file("src/Main.java", "public class Main { public static void main(String[] args) {} }")
        );
        try (ExtractedWorkspace workspace = service.extract(new ByteArrayInputStream(zip))) {
            assertThat(workspace.files()).hasSize(1);
            assertThat(workspace.files().get(0).relativePath()).isEqualTo("src/Main.java");
        }
    }

    @Test
    @DisplayName("Rejects null InputStream with EMPTY_ARCHIVE")
    void extractFromInputStream_nullStream_throwsEmptyArchive() {
        assertThatThrownBy(() -> service.extract((InputStream) null))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPTY_ARCHIVE);
    }

    @Test
    @DisplayName("Rejects empty InputStream with EMPTY_ARCHIVE")
    void extractFromInputStream_emptyStream_throwsEmptyArchive() {
        assertThatThrownBy(() -> service.extract(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPTY_ARCHIVE);
    }

    @Test
    @DisplayName("Rejects InputStream exceeding maxCompressedBytes")
    void extractFromInputStream_exceedsMaxCompressedBytes_throwsException() {
        properties.setMaxCompressedBytes(50);
        byte[] data = new byte[100];
        assertThatThrownBy(() -> service.extract(new ByteArrayInputStream(data)))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE);
    }

    @Test
    @DisplayName("Fails with EXTRACTION_FAILED when InputStream throws IOException")
    void extractFromInputStream_ioException_throwsExtractionFailed() {
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated network stream break");
            }
        };
        assertThatThrownBy(() -> service.extract(failingStream))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXTRACTION_FAILED);
    }

    @Test
    @DisplayName("Rejects byte array exceeding maxCompressedBytes")
    void extractByteArray_exceedsMaxCompressedBytes_throwsException() {
        properties.setMaxCompressedBytes(50);
        byte[] data = new byte[100];
        assertThatThrownBy(() -> service.extract(data))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE);
    }

    @Test
    @DisplayName("Rejects empty or whitespace-only entry name")
    void rejectsEmptyOrWhitespaceEntryName_throwsZipSlip() throws IOException {
        byte[] zip = createZip(
                file("   ", "content")
        );
        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ZIP_SLIP_ATTEMPT);
    }

    @Test
    @DisplayName("Rejects Windows backslash absolute path")
    void rejectsWindowsBackslashAbsolutePath_throwsAbsolutePath() throws IOException {
        byte[] zip = createZip(
                file("\\\\server\\share\\Main.java", "class Main {}")
        );
        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ABSOLUTE_PATH_DISALLOWED);
    }

    @Test
    @DisplayName("Rejects file entry without extension")
    void rejectsFileWithoutExtension_throwsForbiddenFileType() throws IOException {
        byte[] zip = createZip(
                file("LICENSE", "MIT License"),
                file("src/Main.java", "public class Main {}")
        );
        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_FILE_TYPE);
    }

    @Test
    @DisplayName("Rejects zip archive containing no entries with EMPTY_ARCHIVE")
    void emptyZipArchiveNoEntries_throwsEmptyArchive() throws IOException {
        byte[] zip = createZip();
        assertThatThrownBy(() -> service.extract(zip))
                .isInstanceOf(ZipValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPTY_ARCHIVE);
    }

    @Test
    @DisplayName("Filters OS and editor metadata without failing on unpermitted extensions")
    void filtersOsMetadata_andExtractsValidJava() throws IOException {
        byte[] zip = createZip(
                file("__MACOSX/._Main.java", "mac metadata"),
                file("sub/__MACOSX/._Sub.java", "sub mac metadata"),
                file(".DS_Store", "ds store"),
                file("sub/.DS_Store", "sub ds store"),
                file("Thumbs.db", "thumbs"),
                file("sub/Thumbs.db", "sub thumbs"),
                file("src/Main.java", "public class Main { public static void main(String[] args) {} }")
        );

        try (ExtractedWorkspace workspace = service.extract(zip)) {
            assertThat(workspace.files()).hasSize(1);
            assertThat(workspace.files().get(0).relativePath()).isEqualTo("src/Main.java");
        }
    }

    @Test
    @DisplayName("Safely creates explicit directory entries and extracts files inside")
    void extractsWithExplicitDirectoryEntries_succeeds() throws IOException {
        byte[] zip = createZip(
                directory("src"),
                directory("src/nested/"),
                file("src/nested/Main.java", "package nested; public class Main {}")
        );

        try (ExtractedWorkspace workspace = service.extract(zip)) {
            assertThat(workspace.files()).hasSize(1);
            assertThat(workspace.files().get(0).relativePath()).isEqualTo("src/nested/Main.java");
            assertThat(Files.isDirectory(workspace.workspaceDir().resolve("src/nested"))).isTrue();
        }
    }
}
