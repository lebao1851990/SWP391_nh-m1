package com.codeassess.queue.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlagiarismJobPayloadTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private PlagiarismJobPayload.PlagiarismJobPayloadBuilder createValidBuilder() {
        return PlagiarismJobPayload.builder()
                .jobId("job-12345")
                .jobType(JobType.PLAGIARISM_ANALYSIS)
                .schemaVersion("1.0")
                .correlationId("corr-67890")
                .submissionId("sub-abcde")
                .examId("exam-xyz")
                .archiveHash("3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b")
                .sourceTreeHash("9b6452e85a6a24eb87bf59b48b9487b212f7193b2a549e3bf199c0a6b72049d5")
                .timestamp(Instant.now())
                .retryCount(0);
    }

    @Test
    @DisplayName("Valid payload passes all bean validation constraints")
    void testValidPayload() {
        PlagiarismJobPayload payload = createValidBuilder().build();
        Set<ConstraintViolation<PlagiarismJobPayload>> violations = validator.validate(payload);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Blank jobId violates validation constraint")
    void testBlankJobId() {
        PlagiarismJobPayload payload = createValidBuilder().jobId("   ").build();
        Set<ConstraintViolation<PlagiarismJobPayload>> violations = validator.validate(payload);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("jobId");
    }

    @Test
    @DisplayName("Blank submissionId violates validation constraint")
    void testBlankSubmissionId() {
        PlagiarismJobPayload payload = createValidBuilder().submissionId("").build();
        Set<ConstraintViolation<PlagiarismJobPayload>> violations = validator.validate(payload);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("submissionId");
    }

    @Test
    @DisplayName("Blank examId violates validation constraint")
    void testBlankExamId() {
        PlagiarismJobPayload payload = createValidBuilder().examId(null).build();
        Set<ConstraintViolation<PlagiarismJobPayload>> violations = validator.validate(payload);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("examId");
    }

    @Test
    @DisplayName("Negative retryCount violates validation constraint")
    void testNegativeRetryCount() {
        // Constructor normalizes negative retryCount to 0, or if injected directly
        PlagiarismJobPayload payload = createValidBuilder().retryCount(-5).build();
        assertThat(payload.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("JSON serialization and deserialization preserve all versioned fields")
    void testSerializationRoundTrip() throws Exception {
        PlagiarismJobPayload original = createValidBuilder().build();

        String json = objectMapper.writeValueAsString(original);
        assertThat(json).contains("\"jobId\":\"job-12345\"");
        assertThat(json).contains("\"jobType\":\"PLAGIARISM_ANALYSIS\"");
        assertThat(json).contains("\"schemaVersion\":\"1.0\"");
        assertThat(json).contains("\"correlationId\":\"corr-67890\"");
        assertThat(json).contains("\"submissionId\":\"sub-abcde\"");
        assertThat(json).contains("\"examId\":\"exam-xyz\"");

        PlagiarismJobPayload deserialized = objectMapper.readValue(json, PlagiarismJobPayload.class);
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getJobId()).isEqualTo(original.getJobId());
        assertThat(deserialized.getCorrelationId()).isEqualTo(original.getCorrelationId());
        assertThat(deserialized.getSchemaVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("Schema version defaults to 1.0 when not specified")
    void testSchemaVersionDefault() {
        PlagiarismJobPayload payload = PlagiarismJobPayload.builder()
                .jobId("job-default")
                .correlationId("corr-default")
                .submissionId("sub-default")
                .examId("exam-default")
                .build();

        assertThat(payload.getSchemaVersion()).isEqualTo("1.0");
        assertThat(payload.getJobType()).isEqualTo(JobType.PLAGIARISM_ANALYSIS);
        assertThat(payload.getTimestamp()).isNotNull();
    }
}
