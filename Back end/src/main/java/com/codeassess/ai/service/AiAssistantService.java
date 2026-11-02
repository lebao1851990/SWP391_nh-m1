package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.AiAnalysisResponse;
import com.codeassess.ai.dto.AiCodeReviewRequest;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAssistantService {

    private final SimilarityPairRepository similarityPairRepository;
    private final PlagiarismEvidenceRepository plagiarismEvidenceRepository;
    private final UserRepository userRepository;
    private final AiClient aiClient;

    public AiAnalysisResponse getSimilarityExplanation(Long pairId) {
        SimilarityPair pair = getAuthorizedPair(pairId);
        List<PlagiarismEvidence> evidenceList = plagiarismEvidenceRepository.findBySimilarityPair_Id(pairId);

        StringBuilder prompt = new StringBuilder();
        prompt.append(com.codeassess.ai.prompt.AiPromptTemplates.PLAGIARISM_SIMILARITY_EXPLANATION_SYSTEM).append("\n\n");
        prompt.append("Provide a neutral, educational explanation of why the two submissions are structurally similar based on the following metrics and evidence.\n\n");
        
        prompt.append("Similarity Rate: ").append(pair.getSimilarityRate()).append("%\n");
        prompt.append("Shared Code Fragments: ").append(pair.getSharedHashesCount()).append("\n");
        prompt.append("Is Flagged for Review: ").append(pair.getIsFlagged() != null && pair.getIsFlagged() ? "Yes" : "No").append("\n\n");
        
        prompt.append("Evidence Descriptions:\n");
        for (PlagiarismEvidence e : evidenceList) {
            prompt.append("- [").append(e.getEvidenceType()).append("] ").append(e.getDescription()).append("\n");
        }

        try {
            String aiResponse = aiClient.generateSimilarityExplanation(prompt.toString());
            return new AiAnalysisResponse(aiResponse);
        } catch (com.codeassess.ai.exception.AiIntegrationException ex) {
            org.slf4j.LoggerFactory.getLogger(AiAssistantService.class).warn("AI provider error: {}", ex.getMessage());
            return new AiAnalysisResponse("[THÔNG BÁO] Không thể kết nối dịch vụ AI: " + ex.getMessage() + ". Vui lòng đối chiếu trực quan bằng cây AST.");
        }
    }

    public AiAnalysisResponse getCodeReview(Long pairId, AiCodeReviewRequest request) {
        SimilarityPair pair = getAuthorizedPair(pairId);
        PlagiarismEvidence evidence = plagiarismEvidenceRepository.findById(request.evidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found."));

        if (!evidence.getSimilarityPair().getId().equals(pairId)) {
            throw new ResourceNotFoundException("Evidence does not belong to the requested similarity pair.");
        }

        String sourceA = extractLines(pair.getSubmissionA().getSourceCode(), evidence.getStartLineA(), evidence.getEndLineA());
        String sourceB = extractLines(pair.getSubmissionB().getSourceCode(), evidence.getStartLineB(), evidence.getEndLineB());

        StringBuilder prompt = new StringBuilder();
        prompt.append(com.codeassess.ai.prompt.AiPromptTemplates.PLAGIARISM_CODE_REVIEW_SYSTEM).append("\n\n");
        prompt.append("Analyze the following two code snippets which were flagged for structural similarity.\n\n");

        prompt.append("Evidence Type: ").append(evidence.getEvidenceType()).append("\n");
        prompt.append("Match Description: ").append(evidence.getDescription()).append("\n\n");

        prompt.append("Snippet A:\n```\n").append(sourceA).append("\n```\n\n");
        prompt.append("Snippet B:\n```\n").append(sourceB).append("\n```\n");

        try {
            String aiResponse = aiClient.generateCodeReview(prompt.toString());
            return new AiAnalysisResponse(aiResponse);
        } catch (com.codeassess.ai.exception.AiIntegrationException ex) {
            org.slf4j.LoggerFactory.getLogger(AiAssistantService.class).warn("AI provider error: {}", ex.getMessage());
            return new AiAnalysisResponse("[THÔNG BÁO] Dịch vụ AI tạm thời không phản hồi: " + ex.getMessage());
        }
    }

    private SimilarityPair getAuthorizedPair(Long pairId) {
        SimilarityPair pair = similarityPairRepository.findById(pairId)
                .orElseThrow(() -> new ResourceNotFoundException("Similarity pair not found."));

        Exam exam = pair.getExam();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResourceNotFoundException("Similarity pair not found.");
        }
        
        String currentUsername = auth.getName();
        User currentUser = userRepository.findByEmail(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Similarity pair not found."));

        if (exam.getCourseClass().getTeacher() == null || 
            !exam.getCourseClass().getTeacher().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Similarity pair not found."); // Masking 403 as 404
        }

        return pair;
    }

    private String extractLines(String sourceCode, Integer start, Integer end) {
        if (sourceCode == null || start == null || end == null) return "Source range not available";
        String[] lines = sourceCode.split("\r?\n");
        int startIndex = Math.max(0, start - 1);
        int endIndex = Math.min(lines.length - 1, end - 1);

        if (startIndex > endIndex || startIndex >= lines.length) {
            return "Invalid line range";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i <= endIndex; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }
}
