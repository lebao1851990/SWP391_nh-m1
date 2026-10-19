package com.codeassess.submission.dto;

import java.nio.file.Path;

/**
 * Metadata and pointer to an individual safely extracted file.
 */
public record ExtractedFile(
        String relativePath,
        Path absolutePath,
        long sizeBytes,
        boolean isJavaSource
) {}
