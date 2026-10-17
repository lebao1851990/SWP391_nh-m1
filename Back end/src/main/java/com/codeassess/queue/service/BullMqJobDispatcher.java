package com.codeassess.queue.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Dispatches grading jobs from Spring Boot to the Node.js BullMQ producer service.
 * Resilient to queue offline states: failures are caught, logged, and leave the
 * authoritative database record intact for background recovery.
 */
@Service
@Slf4j
public class BullMqJobDispatcher {

    private final RestClient restClient;
    private final boolean enabled;
    private final String producerUrl;

    public BullMqJobDispatcher(
            @Value("${queue.bullmq.enabled:true}") boolean enabled,
            @Value("${queue.bullmq.producer-url:http://localhost:3001}") String producerUrl,
            @Value("${queue.bullmq.timeout-ms:3000}") int timeoutMs) {

        this.enabled = enabled;
        this.producerUrl = (producerUrl != null && !producerUrl.trim().isEmpty()) ? producerUrl.trim() : "http://localhost:3001";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(this.producerUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Attempts to enqueue a grading job to BullMQ.
     *
     * @param submissionId the submission ID
     * @param examId the exam ID
     * @param submissionType SINGLE_FILE or ZIP
     * @return true if enqueued successfully; false if disabled or BullMQ service unreachable
     */
    public boolean dispatchGradingJob(String submissionId, String examId, String submissionType) {
        if (!enabled) {
            log.info("BullMQ queue dispatch is disabled by configuration; skipping submission {}", submissionId);
            return false;
        }

        try {
            Map<String, String> payload = Map.of(
                    "submissionId", submissionId,
                    "examId", examId != null ? examId : "",
                    "submissionType", submissionType != null ? submissionType : "SINGLE_FILE"
            );

            restClient.post()
                    .uri("/api/jobs/grade")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully enqueued grading job for submission {} to BullMQ at {}", submissionId, producerUrl);
            return true;

        } catch (Exception e) {
            log.warn("BullMQ producer unreachable at {} for submission {}. Submission remains PENDING in MySQL: {}",
                    producerUrl, submissionId, e.getMessage());
            return false;
        }
    }
}
