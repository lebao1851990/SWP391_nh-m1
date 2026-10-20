package com.codeassess.submission.service;

import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.ExtractedWorkspace;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceCleanupTest {

    private ZipExtractionService extractionService;

    @BeforeEach
    void setUp() {
        ZipSubmissionProperties properties = new ZipSubmissionProperties();
        extractionService = new ZipExtractionService(properties);
    }

    private byte[] createSampleZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(baos)) {
            // File 1 in root
            ZipArchiveEntry entry1 = new ZipArchiveEntry("Main.java");
            byte[] c1 = "public class Main { public static void main(String[] args) {} }".getBytes(StandardCharsets.UTF_8);
            entry1.setSize(c1.length);
            zaos.putArchiveEntry(entry1);
            zaos.write(c1);
            zaos.closeArchiveEntry();

            // File 2 in nested directory
            ZipArchiveEntry entry2 = new ZipArchiveEntry("utils/Helper.java");
            byte[] c2 = "package utils; public class Helper {}".getBytes(StandardCharsets.UTF_8);
            entry2.setSize(c2.length);
            zaos.putArchiveEntry(entry2);
            zaos.write(c2);
            zaos.closeArchiveEntry();
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Guarantees workspace directory and files are 100% deleted after normal try-with-resources completion")
    void normalCompletion_workspaceCleanedUp() throws Exception {
        byte[] zipBytes = createSampleZip();
        Path capturedWorkspaceDir;

        try (ExtractedWorkspace workspace = extractionService.extract(zipBytes)) {
            capturedWorkspaceDir = workspace.workspaceDir();
            assertThat(capturedWorkspaceDir).isNotNull();
            assertThat(Files.exists(capturedWorkspaceDir)).isTrue();
            assertThat(workspace.files()).hasSize(2);

            for (var extractedFile : workspace.files()) {
                assertThat(Files.exists(extractedFile.absolutePath())).isTrue();
            }
        }

        // After try-with-resources block has closed
        assertThat(Files.exists(capturedWorkspaceDir))
                .as("Temporary workspace directory must be deleted upon closing")
                .isFalse();
    }

    @Test
    @DisplayName("Guarantees workspace directory is 100% deleted even when an exception is thrown inside the block")
    void exceptionInsideTryBlock_workspaceCleanedUp() throws Exception {
        byte[] zipBytes = createSampleZip();
        Path[] capturedWorkspaceDir = new Path[1];

        assertThatThrownBy(() -> {
            try (ExtractedWorkspace workspace = extractionService.extract(zipBytes)) {
                capturedWorkspaceDir[0] = workspace.workspaceDir();
                assertThat(Files.exists(capturedWorkspaceDir[0])).isTrue();
                throw new RuntimeException("Simulated unexpected failure during grading/docker");
            }
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Simulated unexpected failure");

        // Verify directory was still cleaned up
        assertThat(capturedWorkspaceDir[0]).isNotNull();
        assertThat(Files.exists(capturedWorkspaceDir[0]))
                .as("Workspace directory must be cleaned up even if exception is thrown")
                .isFalse();
    }

    @Test
    @DisplayName("Guarantees deep nested directories are recursively deleted without leaving orphans")
    void deepNestedDirectories_cleanedUpCompletely() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(baos)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("a/b/c/d/Deep.java");
            byte[] content = "package a.b.c.d; public class Deep {}".getBytes(StandardCharsets.UTF_8);
            entry.setSize(content.length);
            zaos.putArchiveEntry(entry);
            zaos.write(content);
            zaos.closeArchiveEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        Path capturedWorkspaceDir;
        try (ExtractedWorkspace workspace = extractionService.extract(zipBytes)) {
            capturedWorkspaceDir = workspace.workspaceDir();
            assertThat(Files.exists(capturedWorkspaceDir)).isTrue();
        }

        assertThat(Files.exists(capturedWorkspaceDir))
                .as("Deeply nested hierarchy must be completely removed")
                .isFalse();
    }
}
