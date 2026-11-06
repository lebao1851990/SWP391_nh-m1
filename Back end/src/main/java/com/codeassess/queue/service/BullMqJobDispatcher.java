package com.codeassess.queue.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    public enum JobQueueStatus {
        WAITING,
        ACTIVE,
        DELAYED,
        COMPLETED,
        FAILED,
        NOT_FOUND,
        UNKNOWN
    }

    private final RestClient restClient;
    private final boolean enabled;
    private final String producerUrl;

    @Autowired
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
     * Package-private constructor for unit testing with custom or mocked RestClient.
     */
    BullMqJobDispatcher(RestClient restClient, boolean enabled, String producerUrl) {
        this.restClient = restClient;
        this.enabled = enabled;
        this.producerUrl = (producerUrl != null && !producerUrl.trim().isEmpty()) ? producerUrl.trim() : "http://localhost:3001";
    }

    /**
     * Attempts to enqueue a grading job to BullMQ.
     * Reconciles queue state via GET /api/jobs/grading-{id} if the POST response timed out or failed,
     * ensuring that jobs accepted by Redis but whose responses were lost are not dropped or re-dispatched.
     *
     * @param submissionId the submission ID
     * @param examId the exam ID
     * @param submissionType SINGLE_FILE or ZIP
     * @return true if enqueued successfully or confirmed enqueued via reconciliation; false otherwise
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
            log.warn("BullMQ producer POST error at {} for submission {}: {}. Attempting reconciliation...",
                    producerUrl, submissionId, e.getMessage());
            JobQueueStatus queueStatus = checkJobStatus(submissionId);
            if (isAcceptedStatus(queueStatus)) {
                log.info("Reconciliation confirmed job grading-{} exists in BullMQ (state={}) despite POST error",
                        submissionId, queueStatus);
                return true;
            }
            if (queueStatus == JobQueueStatus.NOT_FOUND) {
                log.warn("Reconciliation confirmed job grading-{} was NOT enqueued in BullMQ (404). Submission remains retry-eligible", submissionId);
            } else {
                log.warn("BullMQ reconciliation unresolved (state=UNKNOWN) for submission {}: {}. Outcome cannot be verified.",
                        submissionId, e.getMessage());
            }
            return false;
        }
    }

    /**
     * Queries BullMQ for the detailed state of a job: GET /api/jobs/grading-{submissionId}.
     * Distinguishes WAITING, ACTIVE, DELAYED, COMPLETED, FAILED, NOT_FOUND (404), and UNKNOWN (network/timeout).
     *
     * @param submissionId the submission ID
     * @return JobQueueStatus enum representing the exact queue state
     */
    public JobQueueStatus checkJobStatus(String submissionId) {
        if (!enabled || submissionId == null || submissionId.isBlank()) {
            return JobQueueStatus.UNKNOWN;
        }
        try {
            var response = restClient.get()
                    .uri("/api/jobs/grading-" + submissionId)
                    .retrieve()
                    .toEntity(Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String state = (String) response.getBody().get("state");
                log.info("Job grading-{} status check returned state={}", submissionId, state);
                if (state != null) {
                    switch (state.trim().toLowerCase()) {
                        case "waiting": return JobQueueStatus.WAITING;
                        case "active": return JobQueueStatus.ACTIVE;
                        case "delayed": return JobQueueStatus.DELAYED;
                        case "completed": return JobQueueStatus.COMPLETED;
                        case "failed": return JobQueueStatus.FAILED;
                        default: return JobQueueStatus.ACTIVE;
                    }
                }
                return JobQueueStatus.ACTIVE;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.debug("Job grading-{} not found in BullMQ (404)", submissionId);
            return JobQueueStatus.NOT_FOUND;
        } catch (Exception e) {
            log.warn("Could not verify BullMQ job grading-{} due to network/timeout error: {}", submissionId, e.getMessage());
            return JobQueueStatus.UNKNOWN;
        }
        return JobQueueStatus.UNKNOWN;
    }

    /**
     * Checks if a job is currently present or was accepted in BullMQ.
     *
     * @param submissionId the submission ID
     * @return true if job state is WAITING, ACTIVE, DELAYED, COMPLETED, or FAILED; false otherwise
     */
    public boolean isJobEnqueued(String submissionId) {
        return isAcceptedStatus(checkJobStatus(submissionId));
    }

    private boolean isAcceptedStatus(JobQueueStatus status) {
        return status == JobQueueStatus.WAITING
                || status == JobQueueStatus.ACTIVE
                || status == JobQueueStatus.DELAYED
                || status == JobQueueStatus.COMPLETED
                || status == JobQueueStatus.FAILED;
    }
}
