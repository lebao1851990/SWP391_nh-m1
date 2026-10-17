package com.codeassess.queue.service;

import com.codeassess.queue.config.RedisQueueProperties;
import com.codeassess.queue.dto.JobType;
import com.codeassess.queue.dto.PlagiarismJobPayload;
import com.codeassess.queue.exception.QueuePublicationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Producer service for serializing and enqueuing versioned plagiarism analysis jobs
 * onto the configured Redis Stream.
 */
@Service
public class PlagiarismJobProducer {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismJobProducer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisQueueProperties properties;
    private final Validator validator;

    public PlagiarismJobProducer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RedisQueueProperties properties,
            Validator validator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.validator = validator;
    }

    /**
     * Publishes a validated plagiarism job payload to the configured Redis Stream.
     *
     * @param payload the job payload to publish
     * @return the Redis Stream RecordId as string, or null if queue publishing is disabled
     * @throws QueuePublicationException if validation, serialization, or Redis publication fails
     */
    public String publish(PlagiarismJobPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("PlagiarismJobPayload must not be null");
        }

        if (!properties.isEnabled()) {
            log.info("Queue publishing is disabled by configuration; skipping job {}", payload.getJobId());
            return null;
        }

        // 1. Validate payload invariants
        Set<ConstraintViolation<PlagiarismJobPayload>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Invalid job payload: ");
            for (ConstraintViolation<PlagiarismJobPayload> v : violations) {
                sb.append(v.getPropertyPath()).append(" ").append(v.getMessage()).append("; ");
            }
            throw new QueuePublicationException(sb.toString().trim());
        }

        // 2. Serialize payload to JSON
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize plagiarism job {}: {}", payload.getJobId(), e.getMessage());
            throw new QueuePublicationException("JSON serialization failed for job " + payload.getJobId(), e);
        }

        // 3. Publish to Redis Stream with explicit string fields
        Map<String, String> streamFields = Map.of(
                "jobId", payload.getJobId(),
                "jobType", payload.getJobType().name(),
                "schemaVersion", payload.getSchemaVersion(),
                "correlationId", payload.getCorrelationId(),
                "payload", jsonPayload
        );

        try {
            RecordId recordId = redisTemplate.opsForStream().add(
                    StreamRecords.string(streamFields).withStreamKey(properties.getStreamName())
            );

            if (recordId == null) {
                throw new QueuePublicationException("Redis returned null RecordId for stream " + properties.getStreamName());
            }

            // Omit raw database IDs, code, and hashes from logs to protect telemetry
            log.info("Successfully published job {} [correlation: {}] to stream {}",
                    payload.getJobId(), payload.getCorrelationId(), properties.getStreamName());

            return recordId.getValue();

        } catch (RedisConnectionFailureException e) {
            log.error("Redis connectivity failure while publishing job {} to stream {}: {}",
                    payload.getJobId(), properties.getStreamName(), e.getMessage());
            throw new QueuePublicationException("Redis broker unreachable during job publication: " + payload.getJobId(), e);
        } catch (Exception e) {
            log.error("Unexpected failure publishing job {} to stream {}: {}",
                    payload.getJobId(), properties.getStreamName(), e.getMessage());
            throw new QueuePublicationException("Failed to publish job " + payload.getJobId() + " to stream " + properties.getStreamName(), e);
        }
    }

    /**
     * Factory and publication helper constructing a versioned payload and publishing it.
     */
    public String createAndPublish(String examId, String submissionId, String archiveHash, String sourceTreeHash) {
        String jobId = "job-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        PlagiarismJobPayload payload = PlagiarismJobPayload.builder()
                .jobId(jobId)
                .jobType(JobType.PLAGIARISM_ANALYSIS)
                .schemaVersion(PlagiarismJobPayload.CURRENT_SCHEMA_VERSION)
                .correlationId(correlationId)
                .submissionId(submissionId)
                .examId(examId)
                .archiveHash(archiveHash)
                .sourceTreeHash(sourceTreeHash)
                .timestamp(Instant.now())
                .retryCount(0)
                .build();

        return publish(payload);
    }

    /**
     * Publishes a versioned matrix refresh job onto the configured Redis Stream.
     */
    public String publishMatrixRefresh(String examId, Long requestedGeneration) {
        String jobId = "matrix-" + examId + "-gen" + (requestedGeneration != null ? requestedGeneration : "curr");
        String correlationId = "corr-matrix-" + UUID.randomUUID().toString().substring(0, 8);

        PlagiarismJobPayload payload = PlagiarismJobPayload.builder()
                .jobId(jobId)
                .jobType(JobType.MATRIX_REFRESH)
                .schemaVersion(PlagiarismJobPayload.CURRENT_SCHEMA_VERSION)
                .correlationId(correlationId)
                .submissionId("ALL")
                .examId(examId)
                .timestamp(Instant.now())
                .retryCount(0)
                .build();

        return publish(payload);
    }
}
