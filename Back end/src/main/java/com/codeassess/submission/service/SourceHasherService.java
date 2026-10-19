package com.codeassess.submission.service;

import com.codeassess.submission.dto.ExtractedFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Computes archive hashes and deterministic source-tree hashes for CodeAssess submissions.
 *
 * <p>Two separate concepts are implemented:
 * <ol>
 *   <li><strong>Archive Hash:</strong> SHA-256 over the exact original uploaded ZIP bytes.</li>
 *   <li><strong>Source Tree Hash:</strong> A deterministic hash calculated across accepted
 *       source files to produce a canonical fingerprint invariant to ZIP packaging tool,
 *       OS file order, file timestamps, trailing whitespace, or Windows/UNIX line endings.</li>
 * </ol>
 */
@Service
public class SourceHasherService {

    private static final String SHA_256 = "SHA-256";

    /**
     * Calculates the SHA-256 hash of an exact byte array (e.g. uploaded archive bytes).
     *
     * @param bytes raw data bytes
     * @return 64-character lowercase hexadecimal string
     */
    public String calculateArchiveSha256(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Input bytes cannot be null");
        }
        return computeSha256Hex(bytes);
    }

    /**
     * Calculates the SHA-256 hash of an input stream.
     *
     * @param inputStream stream to hash
     * @return 64-character lowercase hexadecimal string
     * @throws IOException on stream read failure
     */
    public String calculateArchiveSha256(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }

    /**
     * Calculates a deterministic source-tree hash for the extracted files.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Filters only accepted Java source files ({@code isJavaSource == true}).</li>
     *   <li>Sorts relative paths lexicographically in standard UTF-8 byte order.</li>
     *   <li>Normalizes path separators to forward slashes ({@code /}).</li>
     *   <li>Normalizes source code text:
     *     <ul>
     *       <li>Converts all line endings (CRLF and CR) to standard LF ({@code \n}).</li>
     *       <li>Strips trailing whitespace from each line.</li>
     *       <li>Removes redundant trailing empty lines at end of file.</li>
     *       <li>Appends a single final {@code \n} for non-empty files.</li>
     *     </ul>
     *   </li>
     *   <li>Encodes using unambiguous length-prefixed framing:
     *       {@code "ENTRY:" + pathLength + ":" + path + ":" + contentLength + ":"} followed by normalized bytes.
     *       This mathematical framing prevents cross-file boundary collisions.
     *   </li>
     * </ol>
     *
     * @param files extracted files from the submission workspace
     * @return 64-character lowercase hexadecimal string
     * @throws IOException if a file cannot be read
     */
    public String calculateSourceTreeSha256(List<ExtractedFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }

        List<ExtractedFile> javaFiles = files.stream()
                .filter(ExtractedFile::isJavaSource)
                .sorted(Comparator.comparing(f -> f.relativePath().replace('\\', '/')))
                .toList();

        if (javaFiles.isEmpty()) {
            throw new IllegalArgumentException("No Java source files available to calculate source-tree hash");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);

            for (ExtractedFile file : javaFiles) {
                String normalizedPath = file.relativePath().replace('\\', '/');
                String rawContent = Files.readString(file.absolutePath(), StandardCharsets.UTF_8);
                String normalizedContent = normalizeSourceCode(rawContent);

                byte[] pathBytes = normalizedPath.getBytes(StandardCharsets.UTF_8);
                byte[] contentBytes = normalizedContent.getBytes(StandardCharsets.UTF_8);

                // Unambiguous framing header: ENTRY:<pathLen>:<path>:<contentLen>:
                String header = "ENTRY:" + pathBytes.length + ":" + normalizedPath + ":" + contentBytes.length + ":";
                digest.update(header.getBytes(StandardCharsets.UTF_8));
                digest.update(contentBytes);
            }

            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }

    /**
     * In-memory variant of {@link #calculateSourceTreeSha256(List)} for testing and single-file comparisons.
     *
     * @param relativePathToContent map of relative path to raw source code content
     * @return 64-character lowercase hexadecimal string
     */
    public String calculateSourceTreeSha256(Map<String, String> relativePathToContent) {
        if (relativePathToContent == null || relativePathToContent.isEmpty()) {
            throw new IllegalArgumentException("File content map cannot be null or empty");
        }

        List<String> sortedPaths = relativePathToContent.keySet().stream()
                .filter(p -> p.toLowerCase(Locale.ROOT).endsWith(".java"))
                .sorted(Comparator.comparing(p -> p.replace('\\', '/')))
                .toList();

        if (sortedPaths.isEmpty()) {
            throw new IllegalArgumentException("No Java source files found in content map");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);

            for (String rawPath : sortedPaths) {
                String normalizedPath = rawPath.replace('\\', '/');
                String rawContent = relativePathToContent.get(rawPath);
                String normalizedContent = normalizeSourceCode(rawContent);

                byte[] pathBytes = normalizedPath.getBytes(StandardCharsets.UTF_8);
                byte[] contentBytes = normalizedContent.getBytes(StandardCharsets.UTF_8);

                String header = "ENTRY:" + pathBytes.length + ":" + normalizedPath + ":" + contentBytes.length + ":";
                digest.update(header.getBytes(StandardCharsets.UTF_8));
                digest.update(contentBytes);
            }

            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }

    /**
     * Normalizes line-endings and trailing whitespace.
     *
     * @param rawCode raw source code string
     * @return canonical normalized code string
     */
    public String normalizeSourceCode(String rawCode) {
        if (rawCode == null || rawCode.isEmpty()) {
            return "";
        }

        // Standardize line endings to LF
        String standardized = rawCode.replace("\r\n", "\n").replace('\r', '\n');

        String[] lines = standardized.split("\n", -1);
        List<String> cleanedLines = new ArrayList<>(lines.length);

        for (String line : lines) {
            cleanedLines.add(line.stripTrailing());
        }

        // Remove trailing blank lines
        int lastNonEmptyIndex = cleanedLines.size() - 1;
        while (lastNonEmptyIndex >= 0 && cleanedLines.get(lastNonEmptyIndex).isEmpty()) {
            lastNonEmptyIndex--;
        }

        if (lastNonEmptyIndex < 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= lastNonEmptyIndex; i++) {
            sb.append(cleanedLines.get(i)).append('\n');
        }
        return sb.toString();
    }

    private String computeSha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return toHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
