package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.submission.dto.response.SubmissionFileDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class SubmissionStorageService {

    private final Path storageDirectory;

    public SubmissionStorageService(@Value("${submission.storage.dir:./data/submissions}") String storageDir) {
        this.storageDirectory = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (IOException e) {
            log.error("Failed to initialize submission storage directory: {}", this.storageDirectory, e);
        }
    }

    public String saveZip(String submissionId, byte[] zipBytes) {
        if (submissionId == null || zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("Submission ID and zip bytes must not be empty");
        }
        try {
            Files.createDirectories(storageDirectory);
            Path targetFile = storageDirectory.resolve(submissionId + ".zip").normalize();
            if (!targetFile.startsWith(storageDirectory)) {
                throw new SecurityException("Invalid target file path: path traversal detected");
            }
            Files.write(targetFile, zipBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Saved submission ZIP archive for {} to {}", submissionId, targetFile);
            return targetFile.toString();
        } catch (IOException e) {
            log.error("Failed to persist submission ZIP archive for {}: {}", submissionId, e.getMessage(), e);
            throw new IllegalStateException("Failed to persist submission archive: " + e.getMessage(), e);
        }
    }

    public byte[] getZipBytes(String submissionId) {
        Path targetFile = storageDirectory.resolve(submissionId + ".zip").normalize();
        if (!targetFile.startsWith(storageDirectory) || !Files.exists(targetFile)) {
            throw new ResourceNotFoundException("Submission ZIP archive not found for: " + submissionId);
        }
        try {
            return Files.readAllBytes(targetFile);
        } catch (IOException e) {
            log.error("Failed to read submission ZIP for {}: {}", submissionId, e.getMessage(), e);
            throw new IllegalStateException("Failed to read submission archive: " + e.getMessage(), e);
        }
    }

    public boolean hasZip(String submissionId) {
        Path targetFile = storageDirectory.resolve(submissionId + ".zip").normalize();
        return targetFile.startsWith(storageDirectory) && Files.exists(targetFile);
    }

    public List<SubmissionFileDto> getSubmissionFiles(String submissionId) {
        byte[] zipBytes = getZipBytes(submissionId);
        List<SubmissionFileDto> fileList = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalizedName = entry.getName().replace('\\', '/').trim();
                while (normalizedName.startsWith("/")) {
                    normalizedName = normalizedName.substring(1);
                }
                if (normalizedName.contains("..") || normalizedName.contains("__MACOSX") || normalizedName.endsWith(".DS_Store")) {
                    continue;
                }

                byte[] contentBytes = zis.readAllBytes();
                String content;
                if (normalizedName.toLowerCase().endsWith(".java") 
                        || normalizedName.toLowerCase().endsWith(".txt") 
                        || normalizedName.toLowerCase().endsWith(".json") 
                        || normalizedName.toLowerCase().endsWith(".xml") 
                        || normalizedName.toLowerCase().endsWith(".properties") 
                        || normalizedName.toLowerCase().endsWith(".md")) {
                    content = new String(contentBytes, StandardCharsets.UTF_8);
                } else {
                    content = "[Binary or unsupported text file]";
                }

                String filename = Paths.get(normalizedName).getFileName() != null 
                        ? Paths.get(normalizedName).getFileName().toString() 
                        : normalizedName;

                fileList.add(SubmissionFileDto.builder()
                        .filename(filename)
                        .relativePath(normalizedName)
                        .sizeBytes(entry.getSize() > 0 ? entry.getSize() : contentBytes.length)
                        .content(content)
                        .build());
            }
        } catch (IOException e) {
            log.error("Failed to extract files from submission ZIP {}: {}", submissionId, e.getMessage(), e);
            throw new IllegalStateException("Failed to read files from submission ZIP: " + e.getMessage(), e);
        }

        return fileList;
    }
}
