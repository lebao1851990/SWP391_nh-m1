package com.codeassess.submission.dto;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Encapsulates the safely extracted submission files inside a dedicated temporary workspace directory.
 * Implements {@link AutoCloseable} so it can be used in try-with-resources blocks to guarantee cleanup.
 */
public record ExtractedWorkspace(
        Path workspaceDir,
        List<ExtractedFile> files,
        long totalExtractedBytes,
        String archiveSha256
) implements AutoCloseable {

    @Override
    public void close() {
        if (workspaceDir != null && Files.exists(workspaceDir)) {
            try {
                Files.walkFileTree(workspaceDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
                // Best-effort cleanup
            }
        }
    }
}
