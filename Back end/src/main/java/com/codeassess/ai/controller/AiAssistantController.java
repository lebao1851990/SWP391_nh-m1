package com.codeassess.ai.controller;

import com.codeassess.ai.dto.AiAnalysisResponse;
import com.codeassess.ai.dto.AiCodeReviewRequest;
import com.codeassess.ai.service.AiAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/plagiarism")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    @PostMapping("/{pairId}/ai/similarity-explanation")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AiAnalysisResponse> getSimilarityExplanation(@PathVariable Long pairId) {
        AiAnalysisResponse response = aiAssistantService.getSimilarityExplanation(pairId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{pairId}/ai/code-review")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AiAnalysisResponse> getCodeReview(
            @PathVariable Long pairId,
            @Valid @RequestBody AiCodeReviewRequest request) {
        AiAnalysisResponse response = aiAssistantService.getCodeReview(pairId, request);
        return ResponseEntity.ok(response);
    }
}
