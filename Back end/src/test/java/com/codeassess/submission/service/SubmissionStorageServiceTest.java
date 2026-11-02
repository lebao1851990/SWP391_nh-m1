package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.submission.dto.response.SubmissionFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionStorageServiceTest {

    @TempDir
    Path tempDir;

    private SubmissionStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new SubmissionStorageService(tempDir.toString());
    }

    private byte[] createSampleZip(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("saveZip persists bytes and hasZip/getZipBytes retrieves them")
    void testSaveAndGetZip() throws IOException {
        String subId = "sub-test-123";
        byte[] zipBytes = createSampleZip("Solution.java", "public class Solution {}");

        assertThat(storageService.hasZip(subId)).isFalse();
        String savedPath = storageService.saveZip(subId, zipBytes);
        assertThat(savedPath).contains(subId + ".zip");

        assertThat(storageService.hasZip(subId)).isTrue();
        byte[] retrieved = storageService.getZipBytes(subId);
        assertThat(retrieved).isEqualTo(zipBytes);
        assertThat(storageService.getZipSizeBytes(subId)).isEqualTo((long) zipBytes.length);
    }

    @Test
    @DisplayName("saveZip rejects invalid arguments")
    void testSaveZip_InvalidArgs() {
        assertThatThrownBy(() -> storageService.saveZip(null, new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> storageService.saveZip("sub-1", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> storageService.saveZip("sub-1", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getZipBytes throws ResourceNotFoundException when file does not exist")
    void testGetZipBytes_NotFound() {
        assertThatThrownBy(() -> storageService.getZipBytes("non-existent-sub"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("getSubmissionFiles extracts readable source files and ignores macOS metadata")
    void testGetSubmissionFiles_FiltersMetadataAndExtracts() throws IOException {
        String subId = "sub-files-test";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Java source file
            ZipEntry entry1 = new ZipEntry("src/main/Solution.java");
            zos.putNextEntry(entry1);
            zos.write("class Solution {}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Text README file
            ZipEntry entry2 = new ZipEntry("README.md");
            zos.putNextEntry(entry2);
            zos.write("# Project Docs".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // macOS metadata entry (should be skipped)
            ZipEntry macEntry = new ZipEntry("__MACOSX/._Solution.java");
            zos.putNextEntry(macEntry);
            zos.write(new byte[]{0, 1, 2});
            zos.closeEntry();

            // Directory entry (should be skipped)
            ZipEntry dirEntry = new ZipEntry("src/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
        }

        storageService.saveZip(subId, baos.toByteArray());
        List<SubmissionFileDto> files = storageService.getSubmissionFiles(subId);

        assertThat(files).hasSize(2);
        assertThat(files).extracting(SubmissionFileDto::getFilename)
                .containsExactlyInAnyOrder("Solution.java", "README.md");
        assertThat(files).filteredOn(f -> f.getFilename().equals("Solution.java"))
                .extracting(SubmissionFileDto::getContent)
                .containsExactly("class Solution {}");
    }

    @Test
    @DisplayName("createZipFromSourceCode creates valid zip byte stream")
    void testCreateZipFromSourceCode() {
        byte[] zipBytes = storageService.createZipFromSourceCode("Main.java", "public class Main { public static void main(String[] args) {} }");
        assertThat(zipBytes).isNotEmpty();

        String subId = "source-zip-test";
        storageService.saveZip(subId, zipBytes);
        List<SubmissionFileDto> extracted = storageService.getSubmissionFiles(subId);

        assertThat(extracted).hasSize(1);
        assertThat(extracted.get(0).getFilename()).isEqualTo("Main.java");
        assertThat(extracted.get(0).getContent()).contains("public static void main");
    }

    @Test
    @DisplayName("getZipSizeBytes returns null for null or missing ID")
    void testGetZipSizeBytes_NullOrMissing() {
        assertThat(storageService.getZipSizeBytes(null)).isNull();
        assertThat(storageService.getZipSizeBytes("missing-id")).isNull();
    }
}

