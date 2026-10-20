package com.codeassess.queue.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, versioned job payload for asynchronous plagiarism analysis.
 * Contains only opaque resource identifiers, correlation tracing, and optional integrity hashes.
 * Strictly excludes raw source code, credentials, connection strings, or binary archives.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public final class PlagiarismJobPayload {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    @NotBlank(message = "jobId must not be blank")
    private final String jobId;

    @NotNull(message = "jobType must not be null")
    private final JobType jobType;

    @NotBlank(message = "schemaVersion must not be blank")
    private final String schemaVersion;

    @NotBlank(message = "correlationId must not be blank")
    private final String correlationId;

    @NotBlank(message = "submissionId must not be blank")
    private final String submissionId;

    @NotBlank(message = "examId must not be blank")
    private final String examId;

    private final String archiveHash;

    private final String sourceTreeHash;

    @NotNull(message = "timestamp must not be null")
    private final Instant timestamp;

    @PositiveOrZero(message = "retryCount must be non-negative")
    private final int retryCount;

    @JsonCreator
    public PlagiarismJobPayload(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("jobType") JobType jobType,
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("submissionId") String submissionId,
            @JsonProperty("examId") String examId,
            @JsonProperty("archiveHash") String archiveHash,
            @JsonProperty("sourceTreeHash") String sourceTreeHash,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("retryCount") int retryCount) {
        this.jobId = jobId;
        this.jobType = jobType != null ? jobType : JobType.PLAGIARISM_ANALYSIS;
        this.schemaVersion = schemaVersion != null ? schemaVersion : CURRENT_SCHEMA_VERSION;
        this.correlationId = correlationId;
        this.submissionId = submissionId;
        this.examId = examId;
        this.archiveHash = archiveHash;
        this.sourceTreeHash = sourceTreeHash;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.retryCount = Math.max(0, retryCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlagiarismJobPayload that = (PlagiarismJobPayload) o;
        return retryCount == that.retryCount &&
                Objects.equals(jobId, that.jobId) &&
                jobType == that.jobType &&
                Objects.equals(schemaVersion, that.schemaVersion) &&
                Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(submissionId, that.submissionId) &&
                Objects.equals(examId, that.examId) &&
                Objects.equals(archiveHash, that.archiveHash) &&
                Objects.equals(sourceTreeHash, that.sourceTreeHash) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, jobType, schemaVersion, correlationId, submissionId, examId, archiveHash, sourceTreeHash, timestamp, retryCount);
    }
}
