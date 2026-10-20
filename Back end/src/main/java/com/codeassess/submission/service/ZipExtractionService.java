package com.codeassess.submission.service;

import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.exception.ZipValidationException;
import com.codeassess.submission.exception.ZipValidationException.ErrorCode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Validates and safely extracts student ZIP submissions inside a temporary workspace.
 *
 * <p>Enforces strict security constraints:
 * <ul>
 *   <li>Compressed archive size limit (default 10 MiB)</li>
 *   <li>Total cumulative extracted size limit (default 50 MiB)</li>
 *   <li>Individual file size limit (default 2 MiB)</li>
 *   <li>File count limit (default 200 files)</li>
 *   <li>Compression ratio ceiling (default 10:1 for zip bombs)</li>
 *   <li>Zip Slip directory traversal defense</li>
 *   <li>Absolute and drive-prefixed path rejection</li>
 *   <li>Duplicate and case-colliding entry rejection</li>
 *   <li>Unix symbolic link rejection</li>
 *   <li>Source and auxiliary file extension whitelist</li>
 *   <li>Mandatory presence of at least one valid {@code .java} file</li>
 *   <li>Automatic workspace cleanup on any extraction error or cancellation</li>
 * </ul>
 */
@Service
public class ZipExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractionService.class);

    private final ZipSubmissionProperties properties;

    public ZipExtractionService(ZipSubmissionProperties properties) {
        this.properties = properties;
    }

    /**
     * Extracts and validates an archive supplied as a byte array.
     *
     * @param zipBytes raw archive bytes
     * @return an {@link ExtractedWorkspace} containing the safely extracted files
     */
    public ExtractedWorkspace extract(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new ZipValidationException(ErrorCode.EMPTY_ARCHIVE, "Uploaded archive is empty");
        }
        if (zipBytes.length > properties.getMaxCompressedBytes()) {
            throw new ZipValidationException(ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE,
                    "Uploaded ZIP exceeds maximum compressed size of " + properties.getMaxCompressedBytes() + " bytes");
        }

        String archiveSha256 = computeSha256Hex(zipBytes);
        return doExtract(zipBytes, archiveSha256);
    }

    /**
     * Extracts and validates an archive supplied as an {@link InputStream}.
     *
     * @param inputStream stream containing the archive bytes
     * @return an {@link ExtractedWorkspace} containing the safely extracted files
     */
    public ExtractedWorkspace extract(InputStream inputStream) {
        if (inputStream == null) {
            throw new ZipValidationException(ErrorCode.EMPTY_ARCHIVE, "Uploaded archive stream is null");
        }

        byte[] zipBytes = readStreamBounded(inputStream);
        String archiveSha256 = computeSha256Hex(zipBytes);
        return doExtract(zipBytes, archiveSha256);
    }

    private ExtractedWorkspace doExtract(byte[] zipBytes, String archiveSha256) {
        Path workspaceDir;
        try {
            workspaceDir = Files.createTempDirectory("codeassess-sub-");
        } catch (IOException e) {
            throw new ZipValidationException(ErrorCode.EXTRACTION_FAILED,
                    "Failed to create temporary workspace directory: " + e.getMessage(), e);
        }

        boolean success = false;
        try {
            ExtractedWorkspace workspace = processZipEntries(zipBytes, workspaceDir, archiveSha256);
            success = true;
            return workspace;
        } finally {
            if (!success) {
                cleanupDirectory(workspaceDir);
            }
        }
    }

    private ExtractedWorkspace processZipEntries(byte[] zipBytes, Path workspaceDir, String archiveSha256) {
        int fileCount = 0;
        long totalExtractedBytes = 0;
        int javaFileCount = 0;
        boolean hasEntries = false;

        Set<String> seenNormalizedPaths = new HashSet<>();
        List<ExtractedFile> extractedFiles = new ArrayList<>();

        Path normalizedWorkspace = workspaceDir.toAbsolutePath().normalize();

        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(zipBytes);
             ZipFile zipFile = new ZipFile(channel)) {

            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                hasEntries = true;
                String rawName = entry.getName();

                validateEntryName(rawName);

                if (entry.isUnixSymlink()) {
                    throw new ZipValidationException(ErrorCode.SYMLINK_DISALLOWED,
                            "Symbolic links are strictly forbidden in submissions: " + rawName);
                }

                String normalizedRelative = sanitizeRelativePath(rawName);

                // Discard OS and editor metadata
                if (isIgnoredMetadata(normalizedRelative)) {
                    continue;
                }

                Path resolvedTarget = normalizedWorkspace.resolve(normalizedRelative).normalize();

                // Zip Slip containment check
                if (!resolvedTarget.startsWith(normalizedWorkspace)) {
                    throw new ZipValidationException(ErrorCode.ZIP_SLIP_ATTEMPT,
                            "ZIP Slip directory traversal attempt: " + rawName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedTarget);
                    continue;
                }

                validateExtension(normalizedRelative);

                // Check duplicate and case-colliding paths
                String canonicalPath = normalizedWorkspace.relativize(resolvedTarget).toString().replace('\\', '/');
                String lowerPath = canonicalPath.toLowerCase(Locale.ROOT);
                if (!seenNormalizedPaths.add(lowerPath)) {
                    throw new ZipValidationException(ErrorCode.DUPLICATE_OR_COLLIDING_PATH,
                            "Duplicate or case-colliding file path in archive: " + canonicalPath);
                }

                fileCount++;
                if (fileCount > properties.getMaxFileCount()) {
                    throw new ZipValidationException(ErrorCode.EXCEEDS_MAX_FILE_COUNT,
                            "Archive exceeds maximum file count limit of " + properties.getMaxFileCount());
                }

                if (resolvedTarget.getParent() != null) {
                    Files.createDirectories(resolvedTarget.getParent());
                }

                long individualBytes;
                try (InputStream is = zipFile.getInputStream(entry)) {
                    individualBytes = writeEntryContent(is, resolvedTarget, zipBytes.length, totalExtractedBytes, canonicalPath);
                }
                totalExtractedBytes += individualBytes;

                boolean isJava = isJavaSource(canonicalPath);
                if (isJava) {
                    javaFileCount++;
                }

                extractedFiles.add(new ExtractedFile(canonicalPath, resolvedTarget, individualBytes, isJava));
            }

        } catch (ZipValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new ZipValidationException(ErrorCode.NOT_A_ZIP_ARCHIVE,
                    "Malformed or corrupted ZIP archive: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ZipValidationException(ErrorCode.EXTRACTION_FAILED,
                    "Unexpected extraction failure: " + e.getMessage(), e);
        }

        if (!hasEntries) {
            throw new ZipValidationException(ErrorCode.EMPTY_ARCHIVE, "ZIP archive contains no entries");
        }

        if (javaFileCount == 0) {
            throw new ZipValidationException(ErrorCode.NO_JAVA_SOURCE_FOUND,
                    "Archive does not contain any permitted Java (.java) source files");
        }

        return new ExtractedWorkspace(workspaceDir, extractedFiles, totalExtractedBytes, archiveSha256);
    }

    private void validateEntryName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new ZipValidationException(ErrorCode.ZIP_SLIP_ATTEMPT, "ZIP entry name is empty");
        }
        if (rawName.contains("\0")) {
            throw new ZipValidationException(ErrorCode.ZIP_SLIP_ATTEMPT,
                    "ZIP entry name contains null byte: " + rawName);
        }
        if (rawName.startsWith("/") || rawName.startsWith("\\") || rawName.matches("^[a-zA-Z]:.*")) {
            throw new ZipValidationException(ErrorCode.ABSOLUTE_PATH_DISALLOWED,
                    "Absolute or drive-prefixed paths are forbidden in archive: " + rawName);
        }
        // Direct segment check for ..
        String[] parts = rawName.replace('\\', '/').split("/");
        for (String part : parts) {
            if ("..".equals(part.trim())) {
                throw new ZipValidationException(ErrorCode.ZIP_SLIP_ATTEMPT,
                        "Path traversal segment '..' detected: " + rawName);
            }
        }
    }

    private String sanitizeRelativePath(String rawName) {
        String clean = rawName.replace('\\', '/').trim();
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return clean;
    }

    private boolean isIgnoredMetadata(String path) {
        return path.startsWith("__MACOSX/")
                || path.contains("/__MACOSX/")
                || path.endsWith("/.DS_Store")
                || path.equals(".DS_Store")
                || path.endsWith("/Thumbs.db")
                || path.equals("Thumbs.db");
    }

    private void validateExtension(String relativePath) {
        int lastDot = relativePath.lastIndexOf('.');
        String ext = lastDot >= 0 ? relativePath.substring(lastDot).toLowerCase(Locale.ROOT) : "";

        if (properties.getForbiddenExtensions().contains(ext)) {
            throw new ZipValidationException(ErrorCode.FORBIDDEN_FILE_TYPE,
                    "Forbidden file type in archive: " + relativePath);
        }

        boolean isJava = properties.getAllowedSourceExtensions().contains(ext);
        boolean isAuxiliary = properties.getAllowedAuxiliaryExtensions().contains(ext);

        if (!isJava && !isAuxiliary) {
            throw new ZipValidationException(ErrorCode.FORBIDDEN_FILE_TYPE,
                    "Unpermitted file extension '" + ext + "': " + relativePath);
        }
    }

    private boolean isJavaSource(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".java");
    }

    private long writeEntryContent(InputStream zis, Path target, long compressedArchiveSize, long currentTotalExtracted, String entryName) throws IOException {
        long individualBytes = 0;
        byte[] buffer = new byte[8192];
        int read;

        try (OutputStream os = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            while ((read = zis.read(buffer)) != -1) {
                individualBytes += read;

                if (individualBytes > properties.getMaxIndividualFileBytes()) {
                    throw new ZipValidationException(ErrorCode.EXCEEDS_MAX_FILE_SIZE,
                            "File '" + entryName + "' exceeds maximum allowed size of "
                                    + properties.getMaxIndividualFileBytes() + " bytes");
                }

                long runningTotal = currentTotalExtracted + individualBytes;
                if (runningTotal > properties.getMaxExtractedBytes()) {
                    throw new ZipValidationException(ErrorCode.EXCEEDS_MAX_EXTRACTED_SIZE,
                            "Cumulative extracted size exceeds limit of "
                                    + properties.getMaxExtractedBytes() + " bytes");
                }

                if (runningTotal > properties.getRatioThresholdBytes()) {
                    double ratio = (double) runningTotal / Math.max(1, compressedArchiveSize);
                    if (ratio > properties.getMaxCompressionRatio()) {
                        throw new ZipValidationException(ErrorCode.EXCEEDS_COMPRESSION_RATIO,
                                String.format(Locale.ROOT,
                                        "Suspicious compression ratio (%.1f:1) exceeds limit of %.1f:1",
                                        ratio, properties.getMaxCompressionRatio()));
                    }
                }

                os.write(buffer, 0, read);
            }
        }
        return individualBytes;
    }

    private byte[] readStreamBounded(InputStream inputStream) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        int read;

        try {
            while ((read = inputStream.read(buffer)) != -1) {
                totalRead += read;
                if (totalRead > properties.getMaxCompressedBytes()) {
                    throw new ZipValidationException(ErrorCode.EXCEEDS_MAX_COMPRESSED_SIZE,
                            "Uploaded ZIP exceeds maximum compressed size of " + properties.getMaxCompressedBytes() + " bytes");
                }
                baos.write(buffer, 0, read);
            }
        } catch (ZipValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new ZipValidationException(ErrorCode.EXTRACTION_FAILED,
                    "Failed to read archive input stream: " + e.getMessage(), e);
        }

        if (totalRead == 0) {
            throw new ZipValidationException(ErrorCode.EMPTY_ARCHIVE, "Uploaded ZIP archive is empty");
        }
        return baos.toByteArray();
    }

    private String computeSha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }

    private void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to cleanly remove directory {}: {}", dir, e.getMessage());
        }
    }
}
