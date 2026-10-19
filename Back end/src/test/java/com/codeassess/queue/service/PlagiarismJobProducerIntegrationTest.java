package com.codeassess.queue.service;

import com.codeassess.queue.config.RedisQueueProperties;
import com.codeassess.queue.dto.JobType;
import com.codeassess.queue.dto.PlagiarismJobPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class PlagiarismJobProducerIntegrationTest {

    @Autowired
    private PlagiarismJobProducer producer;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisQueueProperties properties;

    private boolean redisAvailable = false;
    private String testStream;

    @BeforeEach
    void checkRedisAvailability() {
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            redisAvailable = "PONG".equalsIgnoreCase(ping);
        } catch (Exception e) {
            redisAvailable = false;
        }

        testStream = "test:stream:" + UUID.randomUUID();
        properties.setStreamName(testStream);
    }

    @AfterEach
    void cleanup() {
        if (redisAvailable && testStream != null) {
            try {
                redisTemplate.delete(testStream);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("Live Redis Integration: Publish to real Redis stream on localhost:6379 and verify stream record")
    void testLiveRedisStreamPublish() {
        assumeTrue(redisAvailable, "Live Redis on localhost:6379 is not available, skipping integration test");

        PlagiarismJobPayload payload = PlagiarismJobPayload.builder()
                .jobId("job-live-" + UUID.randomUUID())
                .jobType(JobType.PLAGIARISM_ANALYSIS)
                .schemaVersion("1.0")
                .correlationId("corr-live-" + UUID.randomUUID())
                .submissionId("sub-live-123")
                .examId("exam-live-456")
                .archiveHash("sha256-archive-placeholder")
                .sourceTreeHash("sha256-sourcetree-placeholder")
                .timestamp(Instant.now())
                .retryCount(0)
                .build();

        String recordId = producer.publish(payload);

        assertThat(recordId).isNotNull();

        // Read stream from Redis
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(testStream, Range.unbounded());
        assertThat(records).isNotNull().hasSize(1);

        MapRecord<String, Object, Object> record = records.get(0);
        assertThat(record.getId().getValue()).isEqualTo(recordId);
        assertThat(record.getValue().get("jobId")).isEqualTo(payload.getJobId());
        assertThat(record.getValue().get("jobType")).isEqualTo("PLAGIARISM_ANALYSIS");
        assertThat(record.getValue().get("schemaVersion")).isEqualTo("1.0");
        assertThat(record.getValue().get("correlationId")).isEqualTo(payload.getCorrelationId());
        assertThat(record.getValue().get("payload").toString()).contains("\"submissionId\":\"sub-live-123\"");
    }
}
