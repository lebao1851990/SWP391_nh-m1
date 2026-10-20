package com.codeassess.submission.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Configuration limits and file-type constraints for student ZIP submissions.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "submission.zip")
public class ZipSubmissionProperties {

    /** Maximum compressed archive size in bytes (default 10 MiB). */
    private long maxCompressedBytes = 10L * 1024 * 1024;

    /** Maximum cumulative extracted size in bytes (default 50 MiB). */
    private long maxExtractedBytes = 50L * 1024 * 1024;

    /** Maximum uncompressed size for any individual file in bytes (default 2 MiB). */
    private long maxIndividualFileBytes = 2L * 1024 * 1024;

    /** Maximum number of files permitted in the archive (default 200). */
    private int maxFileCount = 200;

    /** Maximum allowed ratio of uncompressed bytes to compressed bytes (default 10:1). */
    private double maxCompressionRatio = 10.0;

    /** Minimum decompressed bytes before compression ratio check activates (default 1 MiB). */
    private long ratioThresholdBytes = 1L * 1024 * 1024;

    /** Permitted source code file extensions (lowercase with leading dot). */
    private Set<String> allowedSourceExtensions = Set.of(".java");

    /** Permitted read-only auxiliary file extensions (configs, datasets). */
    private Set<String> allowedAuxiliaryExtensions = Set.of(
            ".txt", ".json", ".csv", ".xml", ".properties", ".md"
    );

    /** Explicitly forbidden binary, executable, and script extensions. */
    private Set<String> forbiddenExtensions = Set.of(
            ".class", ".jar", ".war", ".ear", ".exe", ".sh", ".bat", ".cmd",
            ".dll", ".so", ".dylib", ".bin", ".py", ".js", ".vbs", ".ps1"
    );
}
