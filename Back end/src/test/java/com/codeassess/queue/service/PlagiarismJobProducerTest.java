package com.codeassess.queue.service;

import com.codeassess.queue.config.RedisQueueProperties;
import com.codeassess.queue.dto.JobType;
import com.codeassess.queue.dto.PlagiarismJobPayload;
import com.codeassess.queue.exception.QueuePublicationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlagiarismJobProducerTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private ObjectMapper objectMapper;
    private RedisQueueProperties properties;
    private Validator validator;

    private PlagiarismJobProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        properties = new RedisQueueProperties();
        properties.setEnabled(true);
        properties.setStreamName("test:stream:plagiarism");

        validator = Validation.buildDefaultValidatorFactory().getValidator();

        producer = new PlagiarismJobProducer(redisTemplate, objectMapper, properties, validator);
    }

    private PlagiarismJobPayload createValidPayload() {
        return PlagiarismJobPayload.builder()
                .jobId("job-001")
                .jobType(JobType.PLAGIARISM_ANALYSIS)
                .schemaVersion("1.0")
                .correlationId("corr-001")
                .submissionId("sub-001")
                .examId("exam-001")
                .archiveHash("hash-a")
                .sourceTreeHash("hash-st")
                .timestamp(Instant.now())
                .retryCount(0)
                .build();
    }

    @Test
    @DisplayName("publish: Valid payload successfully published to Redis Stream with correct fields")
    @SuppressWarnings("unchecked")
    void publish_successfulEnqueue() {
        when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("1710000000000-0"));

        PlagiarismJobPayload payload = createValidPayload();
        String recordId = producer.publish(payload);

        assertThat(recordId).isEqualTo("1710000000000-0");

        ArgumentCaptor<MapRecord<String, String, String>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOps).add(captor.capture());

        MapRecord<String, String, String> captured = captor.getValue();
        assertThat(captured.getStream()).isEqualTo("test:stream:plagiarism");
        assertThat(captured.getValue().get("jobId")).isEqualTo("job-001");
        assertThat(captured.getValue().get("jobType")).isEqualTo("PLAGIARISM_ANALYSIS");
        assertThat(captured.getValue().get("schemaVersion")).isEqualTo("1.0");
        assertThat(captured.getValue().get("correlationId")).isEqualTo("corr-001");
        assertThat(captured.getValue().get("payload")).contains("\"submissionId\":\"sub-001\"");
    }

    @Test
    @DisplayName("publish: Rejects null payload with IllegalArgumentException")
    void publish_nullPayload_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> producer.publish(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("publish: Invalid payload violates validation and throws QueuePublicationException without calling Redis")
    void publish_invalidPayload_throwsQueuePublicationException() {
        PlagiarismJobPayload invalidPayload = PlagiarismJobPayload.builder()
                .jobId("") // Blank
                .submissionId("")
                .examId("")
                .correlationId("")
                .build();

        assertThatThrownBy(() -> producer.publish(invalidPayload))
                .isInstanceOf(QueuePublicationException.class)
                .hasMessageContaining("Invalid job payload");

        verifyNoInteractions(streamOps);
    }

    @Test
    @DisplayName("publish: Serialization failure is caught and wrapped in QueuePublicationException")
    void publish_serializationFailure_throwsQueuePublicationException() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        PlagiarismJobProducer producerWithFailingMapper = new PlagiarismJobProducer(redisTemplate, failingMapper, properties, validator);

        PlagiarismJobPayload payload = createValidPayload();

        assertThatThrownBy(() -> producerWithFailingMapper.publish(payload))
                .isInstanceOf(QueuePublicationException.class)
                .hasMessageContaining("JSON serialization failed");

        verifyNoInteractions(streamOps);
    }

    @Test
    @DisplayName("publish: Redis connectivity failure throws QueuePublicationException with cause")
    @SuppressWarnings("unchecked")
    void publish_redisUnreachable_throwsQueuePublicationException() {
        when(streamOps.add(any(MapRecord.class)))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis on localhost:6379"));

        PlagiarismJobPayload payload = createValidPayload();

        assertThatThrownBy(() -> producer.publish(payload))
                .isInstanceOf(QueuePublicationException.class)
                .hasMessageContaining("Redis broker unreachable")
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("publish: Disabled queue configuration returns null without publishing to Redis")
    void publish_disabledQueue_returnsNull() {
        properties.setEnabled(false);

        PlagiarismJobPayload payload = createValidPayload();
        String recordId = producer.publish(payload);

        assertThat(recordId).isNull();
        verifyNoInteractions(streamOps);
    }

    @Test
    @DisplayName("createAndPublish: Constructs versioned payload and delegates to publish")
    @SuppressWarnings("unchecked")
    void createAndPublish_constructsValidPayloadAndPublishes() {
        when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("1720000000000-1"));

        String recordId = producer.createAndPublish("exam-99", "sub-88", "sha-arch", "sha-st");

        assertThat(recordId).isEqualTo("1720000000000-1");

        ArgumentCaptor<MapRecord<String, String, String>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOps).add(captor.capture());

        MapRecord<String, String, String> captured = captor.getValue();
        assertThat(captured.getValue().get("jobType")).isEqualTo("PLAGIARISM_ANALYSIS");
        assertThat(captured.getValue().get("schemaVersion")).isEqualTo("1.0");
        assertThat(captured.getValue().get("jobId")).startsWith("job-");
        assertThat(captured.getValue().get("correlationId")).startsWith("corr-");
    }
}
