package com.codeassess.submission.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.submission.config.InternalAuthProperties;
import com.codeassess.submission.dto.response.InternalGradingResponse;
import com.codeassess.submission.service.InternalGradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Service-to-service internal API invoked exclusively by the BullMQ Node.js worker.
 * Authenticated via header-based shared secret (X-Internal-Token).
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalSubmissionController {

    private final InternalGradingService internalGradingService;
    private final InternalAuthProperties internalAuthProperties;

    @PostMapping("/submissions/{id}/process-grading")
    public ResponseEntity<?> processGrading(
            @PathVariable("id") String submissionId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken) {

        String expectedSecret = internalAuthProperties.getApiSecret();
        if (internalToken == null || expectedSecret == null || !internalToken.trim().equals(expectedSecret.trim())) {
            log.warn("Unauthorized attempt to access internal grading API for submission {}", submissionId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: invalid or missing internal token"));
        }

        try {
            InternalGradingResponse response = internalGradingService.processGrading(submissionId);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            log.warn("Submission not found for internal grading {}: {}", submissionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Not found"));
        } catch (SandboxException e) {
            log.warn("Retryable sandbox infrastructure failure for submission {}: {}", submissionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Sandbox service unavailable"));
        } catch (Exception e) {
            log.error("Failed to process internal grading for submission {}: {}", submissionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal error"));
        }
    }
}
