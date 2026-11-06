package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.*;
import com.codeassess.ai.exception.AiIntegrationException;
import com.codeassess.ai.exception.AiTruncationException;
import com.codeassess.ai.prompt.AiPromptTemplates;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.scoring.entity.ExamRubricCriteria;
import com.codeassess.scoring.entity.ScoringCriteria;
import com.codeassess.scoring.repository.ExamRubricCriteriaRepository;
import com.codeassess.scoring.repository.ScoringCriteriaRepository;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiExamService {

    private final AiClient aiClient;
    private final ExamRepository examRepository;
    private final CourseClassRepository courseClassRepository;
    private final ScoringCriteriaRepository scoringCriteriaRepository;
    private final ExamRubricCriteriaRepository examRubricCriteriaRepository;
    private final TestCaseRepository testCaseRepository;
    private final DocumentExtractorService documentExtractorService;
    private final ExamQuestionRepository examQuestionRepository;

    private static final BigDecimal TARGET_TOTAL_SCORE = BigDecimal.valueOf(10.00);

    /**
     * Stage 1: Document Analysis Agent.
     * Extracts text safely from PDF/DOCX/TXT and analyzes core topics, concepts, and difficulty.
     */
    public DocumentAnalysisResponse analyzeDocument(MultipartFile file) {
        DocumentExtractorService.ExtractedDocument doc = documentExtractorService.extractText(file);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Please analyze the following academic document and extract key pedagogical elements.\n\n")
                .append("<uploaded_document filename=\"").append(doc.filename()).append("\">\n")
                .append(doc.content())
                .append("\n</uploaded_document>\n\n")
                .append("Return valid JSON with: mainTopic, subTopics, keyConcepts, extractedSnippets, suggestedDifficulty, and suggestedProblemTypes.");

        DocumentAnalysisResponse response = aiClient.generateStructuredOutput(
                AiPromptTemplates.DOCUMENT_ANALYSIS_SYSTEM,
                userPrompt.toString(),
                DocumentAnalysisResponse.class
        );

        response.setDocumentName(doc.filename());
        response.setDocumentSizeBytes(doc.fileSize());
        response.setCharacterCount(doc.characterCount());
        response.setDocumentPreview(doc.previewSnippet());

        return response;
    }

    /**
     * Backward-compatible overload for single-question document exam generation.
     */
    public AiDocumentExamGenerateResponse generateExamFromDocument(
            MultipartFile file,
            String difficulty,
            String programmingLanguage,
            Integer criteriaCount,
            String additionalRequirements) {
        return generateExamFromDocument(file, difficulty, programmingLanguage, 1, -1, criteriaCount, additionalRequirements);
    }

    /**
     * Stage 2 & 3: Multi-question & decomposed Document Exam Generation Agent.
     * Enforces explicit question counts, test case counts per question, outline decomposition,
     * independent question generation, bounded retries of failed components, and actionable Vietnamese error diagnostics.
     */
    public AiDocumentExamGenerateResponse generateExamFromDocument(
            MultipartFile file,
            String difficulty,
            String programmingLanguage,
            Integer questionCount,
            Integer testCasesPerQuestion,
            Integer criteriaCount,
            String additionalRequirements) {

        DocumentExtractorService.ExtractedDocument doc = documentExtractorService.extractText(file);

        String lang = (programmingLanguage != null && !programmingLanguage.isBlank()) ? programmingLanguage.trim() : "Java";
        String diff = (difficulty != null && !difficulty.isBlank()) ? difficulty.trim().toUpperCase() : "MEDIUM";
        int desiredQuestions = (questionCount != null && questionCount >= 1 && questionCount <= 5) ? questionCount : 1;
        int desiredTcs = (testCasesPerQuestion != null && testCasesPerQuestion >= 2 && testCasesPerQuestion <= 6) ? testCasesPerQuestion : -1;
        int desiredCriteria = (criteriaCount != null && criteriaCount >= 2 && criteriaCount <= 6) ? criteriaCount : 4;

        if (desiredQuestions == 1) {
            return generateSingleQuestionExamFromDocument(doc, diff, lang, desiredTcs, desiredCriteria, additionalRequirements);
        } else {
            return generateMultiQuestionExamFromDocument(doc, diff, lang, desiredQuestions, desiredTcs > 0 ? desiredTcs : 3, desiredCriteria, additionalRequirements);
        }
    }

    private AiDocumentExamGenerateResponse generateSingleQuestionExamFromDocument(
            DocumentExtractorService.ExtractedDocument doc,
            String diff,
            String lang,
            int desiredTcs,
            int desiredCriteria,
            String additionalRequirements) {

        AiDocumentExamGenerateResponse response;
        try {
            response = doGenerateExamFromDocument(doc, diff, lang, desiredTcs, desiredCriteria, additionalRequirements);
        } catch (AiTruncationException ex) {
            log.warn("Single question AI document exam generation truncated on initial attempt: {}. Executing controlled bounded retry...", ex.getMessage());
            response = retryWithControlledGeneration(doc, diff, lang, desiredTcs, desiredCriteria, additionalRequirements);
        }

        // Ensure test cases exist
        if (response.getTestCases() == null || response.getTestCases().isEmpty()) {
            log.info("Single question test cases were empty; generating bounded test cases batch...");
            List<AiTestCaseDraftDto> tcs = generateTestCasesForQuestion(
                    response.getTitle(),
                    response.getProblemStatement(),
                    response.getInputSpecification(),
                    response.getOutputSpecification(),
                    response.getSampleInput(),
                    response.getSampleOutput(),
                    lang,
                    desiredTcs
            );
            response.setTestCases(tcs);
        }

        validateTestCasesList(response.getTestCases(), desiredTcs > 0 ? desiredTcs : -1, 1);
        scaleTestCasePoints(response.getTestCases(), BigDecimal.valueOf(10.00));

        response.setMaxScore(BigDecimal.valueOf(10.00));
        response.setRequestedQuestionCount(1);
        response.setGeneratedQuestionCount(1);
        response.setRequestedTestCasesPerQuestion(desiredTcs > 0 ? desiredTcs : response.getTestCases().size());
        response.setGeneratedTestCasesPerQuestion(response.getTestCases().size());

        AiDocumentExamGenerateResponse singleQuestionItem = AiDocumentExamGenerateResponse.builder()
                .title(response.getTitle())
                .problemStatement(response.getProblemStatement())
                .inputSpecification(response.getInputSpecification())
                .outputSpecification(response.getOutputSpecification())
                .constraints(response.getConstraints())
                .sampleInput(response.getSampleInput())
                .sampleOutput(response.getSampleOutput())
                .programmingLanguage(response.getProgrammingLanguage())
                .difficulty(response.getDifficulty())
                .sourceDocumentName(response.getSourceDocumentName())
                .learningObjectives(response.getLearningObjectives() != null ? new ArrayList<>(response.getLearningObjectives()) : new ArrayList<>())
                .sourceReferences(response.getSourceReferences() != null ? new ArrayList<>(response.getSourceReferences()) : new ArrayList<>())
                .testCases(response.getTestCases() != null ? new ArrayList<>(response.getTestCases()) : new ArrayList<>())
                .rubricCriteria(response.getRubricCriteria() != null ? new ArrayList<>(response.getRubricCriteria()) : new ArrayList<>())
                .totalRubricScore(response.getTotalRubricScore())
                .maxScore(response.getMaxScore())
                .questions(Collections.emptyList())
                .build();
        response.setQuestions(List.of(singleQuestionItem));

        validateAndNormalizeDocumentExamResponse(response);
        return response;
    }

    /**
     * Phase C: Multi-question exam generation via outline decomposition, independent question generation,
     * bounded retries of failed components, and validation of requested counts.
     */
    private AiDocumentExamGenerateResponse generateMultiQuestionExamFromDocument(
            DocumentExtractorService.ExtractedDocument doc,
            String diff,
            String lang,
            int desiredQuestions,
            int desiredTcs,
            int desiredCriteria,
            String additionalRequirements) {

        // Step 1: Generate structured exam outline
        AiExamOutlineResponse outline;
        try {
            outline = generateExamOutline(doc, diff, lang, desiredQuestions, additionalRequirements);
        } catch (Exception ex) {
            log.warn("Outline generation failed ({}); attempting bounded outline retry...", ex.getMessage());
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("rate limit")) {
                pacingDelay(4500L);
            }
            try {
                outline = retryGenerateExamOutline(doc, diff, lang, desiredQuestions, additionalRequirements);
            } catch (Exception retryEx) {
                log.error("Outline retry also failed: {}", retryEx.getMessage());
                throw new AiIntegrationException("AI không thể tạo dàn ý cho " + desiredQuestions + " câu hỏi. Vui lòng thử lại.");
            }
        }

        // Step 2: Validate outline
        validateOutline(outline, desiredQuestions);

        // Step 3 & 4: Generate each question & test cases independently
        List<AiDocumentExamGenerateResponse> completedQuestions = new ArrayList<>();
        List<Integer> failedQuestionIndices = new ArrayList<>();
        Map<Integer, String> failureDetails = new LinkedHashMap<>();

        BigDecimal baseQScore = BigDecimal.valueOf(10.00).divide(BigDecimal.valueOf(desiredQuestions), 2, RoundingMode.FLOOR);
        BigDecimal lastQScore = BigDecimal.valueOf(10.00).subtract(baseQScore.multiply(BigDecimal.valueOf(desiredQuestions - 1)));

        for (int i = 0; i < desiredQuestions; i++) {
            if (i > 0) {
                pacingDelay(1500L);
            }
            int qIndex = i + 1;
            AiExamOutlineResponse.AiOutlineItemDto item = outline.getQuestions().get(i);
            BigDecimal qMaxScore = (i == desiredQuestions - 1) ? lastQScore : baseQScore;

            try {
                AiDocumentExamGenerateResponse qResponse = generateSingleQuestionFromOutline(
                        doc, item, diff, lang, desiredCriteria, additionalRequirements, qIndex, desiredQuestions, desiredTcs
                );

                if (qResponse.getTestCases() == null || qResponse.getTestCases().isEmpty() || (desiredTcs > 0 && qResponse.getTestCases().size() != desiredTcs)) {
                    pacingDelay(1500L);
                    List<AiTestCaseDraftDto> tcs = generateTestCasesForQuestion(
                            qResponse.getTitle(),
                            qResponse.getProblemStatement(),
                            qResponse.getInputSpecification(),
                            qResponse.getOutputSpecification(),
                            qResponse.getSampleInput(),
                            qResponse.getSampleOutput(),
                            lang,
                            desiredTcs,
                            qIndex
                    );
                    qResponse.setTestCases(tcs);
                }

                validateTestCasesList(qResponse.getTestCases(), desiredTcs, qIndex);
                scaleTestCasePoints(qResponse.getTestCases(), qMaxScore);

                qResponse.setMaxScore(qMaxScore);
                qResponse.setProgrammingLanguage(lang);
                qResponse.setDifficulty(diff);
                qResponse.setSourceDocumentName(doc.filename());

                // Prevent duplicate questions
                for (AiDocumentExamGenerateResponse prev : completedQuestions) {
                    if (prev.getTitle() != null && prev.getTitle().trim().equalsIgnoreCase(qResponse.getTitle().trim())) {
                        throw new AiIntegrationException("AI sinh câu hỏi trùng lặp tiêu đề với câu trước.");
                    }
                    if (prev.getProblemStatement() != null && prev.getProblemStatement().trim().equalsIgnoreCase(qResponse.getProblemStatement().trim())) {
                        throw new AiIntegrationException("AI sinh câu hỏi trùng lặp nội dung đề bài với câu trước.");
                    }
                }

                completedQuestions.add(qResponse);

            } catch (Exception ex) {
                log.warn("Question {} generation failed: {}. Retrying component...", qIndex, ex.getMessage());
                if (ex.getMessage() != null && (ex.getMessage().toLowerCase().contains("rate limit")
                        || ex.getMessage().toLowerCase().contains("unavailable")
                        || ex.getMessage().toLowerCase().contains("quota")
                        || ex.getMessage().toLowerCase().contains("429")
                        || ex.getMessage().toLowerCase().contains("503"))) {
                    pacingDelay(8000L);
                }
                // Bounded retry of ONLY this failed question (up to 2 retry attempts)
                AiDocumentExamGenerateResponse retryQ = null;
                Exception lastRetryEx = null;
                for (int attempt = 1; attempt <= 2; attempt++) {
                    try {
                        retryQ = retryGenerateSingleQuestion(
                                doc, item, diff, lang, desiredCriteria, additionalRequirements, qIndex, desiredQuestions, desiredTcs, qMaxScore
                        );
                        lastRetryEx = null;
                        break;
                    } catch (Exception retryEx) {
                        lastRetryEx = retryEx;
                        log.warn("Retry attempt {} of question {} failed: {}", attempt, qIndex, retryEx.getMessage());
                        if (attempt < 2) {
                            pacingDelay(8000L * attempt);
                        }
                    }
                }

                if (retryQ != null) {
                    for (AiDocumentExamGenerateResponse prev : completedQuestions) {
                        if (prev.getTitle() != null && prev.getTitle().trim().equalsIgnoreCase(retryQ.getTitle().trim())) {
                            throw new AiIntegrationException("AI sinh câu hỏi trùng lặp tiêu đề với câu trước.");
                        }
                    }
                    completedQuestions.add(retryQ);
                } else {
                    log.error("All retries of question {} failed: {}", qIndex, lastRetryEx != null ? lastRetryEx.getMessage() : "unknown");
                    failedQuestionIndices.add(qIndex);
                    String detailMsg = lastRetryEx != null ? lastRetryEx.getMessage() : ("Câu " + qIndex + " không thể hoàn thành.");
                    if (detailMsg != null && !detailMsg.contains("Câu") && !detailMsg.contains("ở câu")) {
                        detailMsg = "Câu " + qIndex + " " + detailMsg;
                    }
                    failureDetails.put(qIndex, detailMsg);
                }
            }
        }

        // Step 5 & 6: Validate & Assemble
        if (completedQuestions.size() < desiredQuestions) {
            String diagnosticMessage = buildActionableFailureMessage(desiredQuestions, completedQuestions.size(), failedQuestionIndices, failureDetails, desiredTcs);
            log.error("Multi-question generation incomplete: {}", diagnosticMessage);
            throw new AiIntegrationException(diagnosticMessage);
        }

        // Assemble final response
        AiDocumentExamGenerateResponse firstQ = completedQuestions.get(0);
        AiDocumentExamGenerateResponse assembled = AiDocumentExamGenerateResponse.builder()
                .title(outline.getExamTitle() != null && !outline.getExamTitle().isBlank() ? outline.getExamTitle() : firstQ.getTitle())
                .problemStatement(firstQ.getProblemStatement())
                .inputSpecification(firstQ.getInputSpecification())
                .outputSpecification(firstQ.getOutputSpecification())
                .constraints(firstQ.getConstraints())
                .sampleInput(firstQ.getSampleInput())
                .sampleOutput(firstQ.getSampleOutput())
                .programmingLanguage(lang)
                .difficulty(diff)
                .sourceDocumentName(doc.filename())
                .documentTopicSummary(outline.getOverview())
                .learningObjectives(firstQ.getLearningObjectives())
                .sourceReferences(firstQ.getSourceReferences())
                .testCases(firstQ.getTestCases())
                .rubricCriteria(firstQ.getRubricCriteria())
                .totalRubricScore(BigDecimal.valueOf(10.00))
                .maxScore(BigDecimal.valueOf(10.00))
                .requestedQuestionCount(desiredQuestions)
                .generatedQuestionCount(completedQuestions.size())
                .requestedTestCasesPerQuestion(desiredTcs)
                .generatedTestCasesPerQuestion(desiredTcs)
                .questions(completedQuestions)
                .build();

        return assembled;
    }

    private AiExamOutlineResponse generateExamOutline(
            DocumentExtractorService.ExtractedDocument doc,
            String diff,
            String lang,
            int questionCount,
            String additionalRequirements) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a structured outline for an assessment consisting of EXACTLY ")
                .append(questionCount).append(" distinct programming problems grounded in this document:\n\n")
                .append("<uploaded_document filename=\"").append(doc.filename()).append("\">\n")
                .append(doc.previewSnippet() != null && !doc.previewSnippet().isBlank() ? doc.previewSnippet() : doc.content())
                .append("\n</uploaded_document>\n\n")
                .append("Directives:\n")
                .append("- Target Language: ").append(lang).append("\n")
                .append("- Target Difficulty: ").append(diff).append("\n")
                .append("- Number of Questions: EXACTLY ").append(questionCount).append(" distinct questions.\n");

        if (additionalRequirements != null && !additionalRequirements.isBlank()) {
            prompt.append("- Additional Guidelines: ").append(additionalRequirements.trim()).append("\n");
        }

        prompt.append("\nRequirements:\n")
                .append("1. Provide an 'examTitle' and 'overview'.\n")
                .append("2. 'questions' MUST contain an array of EXACTLY ").append(questionCount).append(" outline items.\n")
                .append("3. Each outline item must include: 'questionIndex' (1 to ").append(questionCount).append("), 'title', 'targetConcept', 'shortSummary', 'difficulty'.\n")
                .append("4. Each question MUST address a DIFFERENT concept/topic from the document. No duplicates.\n")
                .append("5. Output MUST be valid raw JSON conforming to AiExamOutlineResponse.");

        return aiClient.generateStructuredOutput(
                AiPromptTemplates.DOCUMENT_EXAM_OUTLINE_SYSTEM,
                prompt.toString(),
                AiExamOutlineResponse.class
        );
    }

    private AiExamOutlineResponse retryGenerateExamOutline(
            DocumentExtractorService.ExtractedDocument doc,
            String diff,
            String lang,
            int questionCount,
            String additionalRequirements) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a concise outline for EXACTLY ").append(questionCount)
                .append(" distinct programming questions based on: ").append(doc.filename()).append("\n\n")
                .append("Context: ").append(doc.previewSnippet() != null ? doc.previewSnippet() : "Key programming topics")
                .append("\nRequirements: Return valid JSON with 'examTitle', 'overview', and 'questions' list of EXACTLY ")
                .append(questionCount).append(" items with 'questionIndex', 'title', 'targetConcept', 'shortSummary'.");

        return aiClient.generateStructuredOutput(
                AiPromptTemplates.DOCUMENT_EXAM_OUTLINE_SYSTEM,
                prompt.toString(),
                AiExamOutlineResponse.class
        );
    }

    private void validateOutline(AiExamOutlineResponse outline, int expectedQuestions) {
        if (outline == null || outline.getQuestions() == null || outline.getQuestions().isEmpty()) {
            throw new AiIntegrationException("AI không thể tạo dàn ý cho " + expectedQuestions + " câu hỏi. Vui lòng thử lại.");
        }
        if (outline.getQuestions().size() != expectedQuestions) {
            log.warn("Outline question count ({}) does not match expected ({})", outline.getQuestions().size(), expectedQuestions);
            throw new AiIntegrationException("AI không thể tạo đủ dàn ý cho " + expectedQuestions + " câu hỏi (chỉ tạo được " + outline.getQuestions().size() + ").");
        }

        Set<String> titles = new HashSet<>();
        for (AiExamOutlineResponse.AiOutlineItemDto item : outline.getQuestions()) {
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                throw new AiIntegrationException("AI trả về dàn ý câu hỏi thiếu tiêu đề.");
            }
            if (item.getShortSummary() == null || item.getShortSummary().isBlank()) {
                throw new AiIntegrationException("AI trả về dàn ý câu hỏi thiếu mô tả tóm tắt.");
            }
            if (!titles.add(item.getTitle().trim().toLowerCase(Locale.ROOT))) {
                throw new AiIntegrationException("AI trả về dàn ý có câu hỏi trùng lặp tiêu đề.");
            }
        }
    }

    private AiDocumentExamGenerateResponse generateSingleQuestionFromOutline(
            DocumentExtractorService.ExtractedDocument doc,
            AiExamOutlineResponse.AiOutlineItemDto outlineItem,
            String diff,
            String lang,
            int criteriaCount,
            String additionalRequirements,
            int qIndex,
            int totalQuestions,
            int desiredTcs) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Synthesize programming problem #").append(qIndex).append(" of ").append(totalQuestions)
                .append(" based on the following outline specification and document context:\n\n")
                .append("Question Title: ").append(outlineItem.getTitle()).append("\n")
                .append("Target Concept: ").append(outlineItem.getTargetConcept()).append("\n")
                .append("Summary: ").append(outlineItem.getShortSummary()).append("\n")
                .append("Language: ").append(lang).append(", Difficulty: ").append(diff).append("\n\n")
                .append("<uploaded_document filename=\"").append(doc.filename()).append("\">\n")
                .append(doc.previewSnippet() != null && !doc.previewSnippet().isBlank() ? doc.previewSnippet() : doc.content())
                .append("\n</uploaded_document>\n\n");

        if (additionalRequirements != null && !additionalRequirements.isBlank()) {
            prompt.append("Additional Directives: ").append(additionalRequirements.trim()).append("\n\n");
        }

        prompt.append("CRITICAL REQUIREMENTS:\n")
                .append("1. Provide 'title', 'problemStatement', 'inputSpecification', 'outputSpecification', 'constraints' (formatted text of constraints/limits), 'sampleInput', 'sampleOutput'.\n")
                .append("2. 'learningObjectives' (2-3 items) and authentic 'sourceReferences' (2-3 citations).\n")
                .append("3. 'rubricCriteria' (").append(criteriaCount).append(" items summing to 10.00).\n")
                .append("4. 'testCases' (EXACTLY ").append(desiredTcs > 0 ? desiredTcs : 4).append(" diverse test cases covering basic, edge, and hidden cases with 'name', 'inputData', 'expectedOutput', 'isHidden', and 'points' summing to 10.00).\n")
                .append("5. Output MUST be valid raw JSON conforming to AiDocumentExamGenerateResponse.");

        AiDocumentExamGenerateResponse response = aiClient.generateStructuredOutput(
                AiPromptTemplates.DOCUMENT_EXAM_GENERATION_SYSTEM,
                prompt.toString(),
                AiDocumentExamGenerateResponse.class
        );

        validateQuestionStructure(response, qIndex);
        return response;
    }

    private AiDocumentExamGenerateResponse retryGenerateSingleQuestion(
            DocumentExtractorService.ExtractedDocument doc,
            AiExamOutlineResponse.AiOutlineItemDto outlineItem,
            String diff,
            String lang,
            int criteriaCount,
            String additionalRequirements,
            int qIndex,
            int totalQuestions,
            int desiredTcs,
            BigDecimal qMaxScore) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate concise programming problem #").append(qIndex).append(" of ").append(totalQuestions)
                .append(": Title='").append(outlineItem.getTitle()).append("', Concept='").append(outlineItem.getTargetConcept()).append("'.\n")
                .append("Language: ").append(lang).append(", Difficulty: ").append(diff).append(".\n")
                .append("Requirements: Return valid JSON with 'title', 'problemStatement', 'inputSpecification', 'outputSpecification', 'constraints', 'sampleInput', 'sampleOutput', 'rubricCriteria' summing to 10.00, and 'testCases' (EXACTLY ")
                .append(desiredTcs > 0 ? desiredTcs : 4).append(" items).");

        AiDocumentExamGenerateResponse response = aiClient.generateStructuredOutput(
                AiPromptTemplates.DOCUMENT_EXAM_GENERATION_SYSTEM,
                prompt.toString(),
                AiDocumentExamGenerateResponse.class
        );

        validateQuestionStructure(response, qIndex);

        if (response.getTestCases() == null || response.getTestCases().isEmpty() || (desiredTcs > 0 && response.getTestCases().size() != desiredTcs)) {
            pacingDelay(1500L);
            List<AiTestCaseDraftDto> tcs = generateTestCasesForQuestion(
                    response.getTitle(),
                    response.getProblemStatement(),
                    response.getInputSpecification(),
                    response.getOutputSpecification(),
                    response.getSampleInput(),
                    response.getSampleOutput(),
                    lang,
                    desiredTcs,
                    qIndex
            );
            response.setTestCases(tcs);
        }

        validateTestCasesList(response.getTestCases(), desiredTcs, qIndex);
        scaleTestCasePoints(response.getTestCases(), qMaxScore);

        response.setMaxScore(qMaxScore);
        response.setProgrammingLanguage(lang);
        response.setDifficulty(diff);
        response.setSourceDocumentName(doc.filename());
        return response;
    }

    private String buildActionableFailureMessage(
            int requestedTotal,
            int generatedCount,
            List<Integer> failedIndices,
            Map<Integer, String> failureDetails,
            int requestedTcs) {

        // Check if there is a specific field or testcase error to surface directly
        for (Map.Entry<Integer, String> entry : failureDetails.entrySet()) {
            String detail = entry.getValue();
            if (detail != null) {
                if (detail.contains("thiếu trường") || detail.contains("thiếu nội dung đề bài")) {
                    return detail.endsWith(".") ? detail : detail + ".";
                }
                if (detail.contains("có test case không hợp lệ") || detail.contains("không hợp lệ")) {
                    return detail.endsWith(".") ? detail : detail + ".";
                }
                if (detail.contains("chỉ có") || detail.contains("thiếu")) {
                    return detail.endsWith(".") ? detail : detail + ".";
                }
                if (detail.contains("cấu trúc testCases không hợp lệ")) {
                    return detail.endsWith(".") ? detail : detail + ".";
                }
            }
        }

        if (failedIndices.size() == 1) {
            return "Không thể hoàn thành câu " + failedIndices.get(0) + " sau số lần thử lại cho phép.";
        }

        if (!failedIndices.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("AI đã tạo ").append(generatedCount).append("/").append(requestedTotal).append(" câu hỏi. Câu ");
            for (int i = 0; i < failedIndices.size(); i++) {
                if (i > 0 && i == failedIndices.size() - 1) {
                    sb.append(" và ");
                } else if (i > 0) {
                    sb.append(", ");
                }
                sb.append(failedIndices.get(i));
            }
            sb.append(" chưa hoàn thành.");
            return sb.toString();
        }

        if (generatedCount == 0) {
            return "AI chưa thể hoàn thành dữ liệu đề thi. Vui lòng thử lại hoặc giảm số lượng câu hỏi.";
        }

        return "AI đã tạo " + generatedCount + "/" + requestedTotal + " câu hỏi. Vui lòng thử lại để hoàn thiện đề thi.";
    }

    private AiDocumentExamGenerateResponse doGenerateExamFromDocument(
            DocumentExtractorService.ExtractedDocument doc,
            String diff,
            String lang,
            int desiredTcs,
            int desiredCriteria,
            String additionalRequirements) {

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Synthesize an original programming assessment directly grounded in this document.\n\n")
                .append("<uploaded_document filename=\"").append(doc.filename()).append("\">\n")
                .append(doc.content())
                .append("\n</uploaded_document>\n\n")
                .append("Instructor Assessment Directives:\n")
                .append("- Target Programming Language: ").append(lang).append("\n")
                .append("- Desired Difficulty Level: ").append(diff).append("\n")
                .append("- Target Rubric Criteria Count: ").append(desiredCriteria).append("\n");

        if (additionalRequirements != null && !additionalRequirements.isBlank()) {
            userPrompt.append("- Additional Guidelines: ").append(additionalRequirements.trim()).append("\n");
        }

        userPrompt.append("\nCRITICAL REQUIREMENTS:\n")
                .append("1. 'title', 'problemStatement', 'inputSpecification', 'outputSpecification', 'constraints', 'sampleInput', 'sampleOutput'.\n")
                .append("2. 'learningObjectives' (2-3 items) and authentic 'sourceReferences' (2-3 authentic citations from the document).\n")
                .append("3. 'testCases' (").append(desiredTcs > 0 ? "EXACTLY " + desiredTcs : "2 to 4").append(" items with realistic inputs/outputs and point weights summing to 10.00).\n")
                .append("4. 'rubricCriteria' (").append(desiredCriteria).append(" items with maxScore summing strictly to 10.00).\n")
                .append("5. Output MUST be valid, raw JSON conforming to AiDocumentExamGenerateResponse.");

        AiDocumentExamGenerateResponse response = aiClient.generateStructuredOutput(
                AiPromptTemplates.DOCUMENT_EXAM_GENERATION_SYSTEM,
                userPrompt.toString(),
                AiDocumentExamGenerateResponse.class
        );

        response.setSourceDocumentName(doc.filename());
        response.setProgrammingLanguage(lang);
        response.setDifficulty(diff);

        validateQuestionStructure(response);

        if (response.getTestCases() == null || response.getTestCases().isEmpty() || (desiredTcs > 0 && response.getTestCases().size() != desiredTcs)) {
            log.info("AI response did not include exact requested test cases ({}/{}); generating bounded test cases batch...",
                    response.getTestCases() != null ? response.getTestCases().size() : 0, desiredTcs);
            List<AiTestCaseDraftDto> generatedTcs = generateTestCasesForQuestion(
                    response.getTitle(),
                    response.getProblemStatement(),
                    response.getInputSpecification(),
                    response.getOutputSpecification(),
                    response.getSampleInput(),
                    response.getSampleOutput(),
                    lang,
                    desiredTcs
            );
            response.setTestCases(generatedTcs);
        }

        validateAndNormalizeDocumentExamResponse(response);
        return response;
    }

    private AiDocumentExamGenerateResponse retryWithControlledGeneration(
            DocumentExtractorService.ExtractedDocument doc,
            String diff,
            String lang,
            int desiredTcs,
            int desiredCriteria,
            String additionalRequirements) {

        try {
            // Stage 1: Generate question problem structure with compact focused prompt (omitting test cases to fit token budget)
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Synthesize a focused programming problem based on this document.\n\n")
                    .append("<uploaded_document filename=\"").append(doc.filename()).append("\">\n")
                    .append(doc.previewSnippet() != null && !doc.previewSnippet().isBlank() ? doc.previewSnippet() : doc.content())
                    .append("\n</uploaded_document>\n\n")
                    .append("Directives:\n")
                    .append("- Language: ").append(lang).append(", Difficulty: ").append(diff).append("\n")
                    .append("- Produce: title, problemStatement, inputSpecification, outputSpecification, constraints, sampleInput, sampleOutput, learningObjectives, sourceReferences, and rubricCriteria (summing to 10.00).\n")
                    .append("- DO NOT generate testCases in this response (focus on problem specification).\n");

            if (additionalRequirements != null && !additionalRequirements.isBlank()) {
                userPrompt.append("- Additional Guidelines: ").append(additionalRequirements.trim()).append("\n");
            }

            AiDocumentExamGenerateResponse response = aiClient.generateStructuredOutput(
                    AiPromptTemplates.DOCUMENT_EXAM_GENERATION_SYSTEM,
                    userPrompt.toString(),
                    AiDocumentExamGenerateResponse.class
            );

            response.setSourceDocumentName(doc.filename());
            response.setProgrammingLanguage(lang);
            response.setDifficulty(diff);

            validateQuestionStructure(response);

            // Stage 2: Generate bounded test cases for this specific question
            List<AiTestCaseDraftDto> testCases = generateTestCasesForQuestion(
                    response.getTitle(),
                    response.getProblemStatement(),
                    response.getInputSpecification(),
                    response.getOutputSpecification(),
                    response.getSampleInput(),
                    response.getSampleOutput(),
                    lang,
                    desiredTcs
            );
            response.setTestCases(testCases);

            validateAndNormalizeDocumentExamResponse(response);
            return response;

        } catch (AiTruncationException te) {
            log.error("Controlled retry also failed due to output truncation: {}", te.getMessage());
            throw new AiIntegrationException("Gemini đã chạm giới hạn đầu ra. AI trả về dữ liệu chưa hoàn chỉnh. Vui lòng giảm số câu hỏi hoặc thử lại.");
        } catch (AiIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Controlled retry failed: {}", ex.getMessage());
            throw new AiIntegrationException("AI provider failed to generate exam content: " + ex.getMessage());
        }
    }

    /**
     * Backward-compatible overload for test case generation.
     */
    public List<AiTestCaseDraftDto> generateTestCasesForQuestion(
            String title,
            String problemStatement,
            String inputSpecification,
            String outputSpecification,
            String sampleInput,
            String sampleOutput,
            String programmingLanguage) {
        return generateTestCasesForQuestion(title, problemStatement, inputSpecification, outputSpecification, sampleInput, sampleOutput, programmingLanguage, -1);
    }

    /**
     * Bounded test case generation for a specific question with explicit count enforcement and duplicate prevention.
     */
    public List<AiTestCaseDraftDto> generateTestCasesForQuestion(
            String title,
            String problemStatement,
            String inputSpecification,
            String outputSpecification,
            String sampleInput,
            String sampleOutput,
            String programmingLanguage,
            int requestedCount) {
        return generateTestCasesForQuestion(title, problemStatement, inputSpecification, outputSpecification, sampleInput, sampleOutput, programmingLanguage, requestedCount, -1);
    }

    /**
     * Bounded test case generation for a specific question with explicit count enforcement, question-scoped validation, and duplicate prevention.
     */
    public List<AiTestCaseDraftDto> generateTestCasesForQuestion(
            String title,
            String problemStatement,
            String inputSpecification,
            String outputSpecification,
            String sampleInput,
            String sampleOutput,
            String programmingLanguage,
            int requestedCount,
            int questionIndex) {

        int targetCount = (requestedCount >= 2 && requestedCount <= 6) ? requestedCount : 3;

        StringBuilder prompt = new StringBuilder();
        if (requestedCount > 0) {
            prompt.append("Generate EXACTLY ").append(targetCount).append(" diverse and robust test cases for the following programming problem:\n\n");
        } else {
            prompt.append("Generate 2 to 4 diverse and robust test cases for the following programming problem:\n\n");
        }
        prompt.append("Problem Title: ").append(title != null ? title : "Problem").append("\n")
                .append("Programming Language: ").append(programmingLanguage != null ? programmingLanguage : "Java").append("\n")
                .append("Problem Statement:\n").append(problemStatement != null ? problemStatement : "").append("\n\n")
                .append("Input Specification: ").append(inputSpecification != null ? inputSpecification : "").append("\n")
                .append("Output Specification: ").append(outputSpecification != null ? outputSpecification : "").append("\n");
        if (sampleInput != null && !sampleInput.isBlank()) {
            prompt.append("Sample Input:\n").append(sampleInput).append("\n");
        }
        if (sampleOutput != null && !sampleOutput.isBlank()) {
            prompt.append("Sample Output:\n").append(sampleOutput).append("\n");
        }
        prompt.append("\nRequirements:\n");
        if (requestedCount > 0) {
            prompt.append("1. Provide EXACTLY ").append(targetCount).append(" comprehensive test cases covering normal execution, edge cases, and at least 1 hidden test case.\n");
        } else {
            prompt.append("1. Provide 2-4 comprehensive test cases covering normal execution, edge cases, and at least 1 hidden test case.\n");
        }
        prompt.append("2. Each test case MUST have a meaningful 'name' (not generic 'tc' or 'dummy'), 'inputData', and non-blank 'expectedOutput'.\n")
                .append("3. NO DUPLICATE test cases: each test case must test a distinct input and have distinct expectedOutput.\n")
                .append("4. 'points' for all test cases must sum up to exactly 10.00.\n")
                .append("5. At least 1 test case must have 'isHidden'=false.\n")
                .append("6. Output MUST be valid JSON conforming to AiTestCaseBatchResponse with field 'testCases'.\n")
                .append("7. Format: {\"testCases\": [{\"name\": \"...\", \"inputData\": \"...\", \"expectedOutput\": \"...\", \"isHidden\": false}]}");

        AiTestCaseBatchResponse batch;
        try {
            batch = aiClient.generateStructuredOutput(
                    "You are CodeAssess Automated Test Engineer AI. Generate thorough test cases for programming problems.",
                    prompt.toString(),
                    AiTestCaseBatchResponse.class
            );
        } catch (Exception ex) {
            log.warn("Test case batch generation failed: {}. Retrying once...", ex.getMessage());
            if (ex.getMessage() != null && (ex.getMessage().toLowerCase().contains("rate limit")
                    || ex.getMessage().toLowerCase().contains("unavailable")
                    || ex.getMessage().toLowerCase().contains("quota")
                    || ex.getMessage().toLowerCase().contains("429")
                    || ex.getMessage().toLowerCase().contains("503"))) {
                pacingDelay(8000L);
            }
            batch = aiClient.generateStructuredOutput(
                    "You are CodeAssess Automated Test Engineer AI. Generate thorough test cases for programming problems.",
                    prompt.toString(),
                    AiTestCaseBatchResponse.class
            );
        }

        if (batch == null || batch.getTestCases() == null || batch.getTestCases().isEmpty()) {
            throw new AiIntegrationException("AI provider failed to generate test cases for the question.");
        }

        List<AiTestCaseDraftDto> testCases = batch.getTestCases();
        if (requestedCount > 0 && testCases.size() != targetCount) {
            log.warn("Generated test cases count ({}) does not match requested target ({}). Retrying test cases...",
                    testCases.size(), targetCount);
            try {
                batch = aiClient.generateStructuredOutput(
                        "You are CodeAssess Automated Test Engineer AI. Return EXACTLY " + targetCount + " test cases.",
                        prompt.toString(),
                        AiTestCaseBatchResponse.class
                );
                if (batch != null && batch.getTestCases() != null) {
                    testCases = batch.getTestCases();
                }
            } catch (Exception ignored) {}
        }

        if (requestedCount > 0 && testCases.size() != targetCount) {
            throw new AiIntegrationException("chỉ có " + testCases.size() + "/" + targetCount + " test cases (thiếu " + (targetCount - testCases.size()) + "/" + targetCount + " test cases)");
        }

        validateTestCasesList(testCases, requestedCount, questionIndex);
        return testCases;
    }

    private void validateTestCasesList(List<AiTestCaseDraftDto> testCases) {
        validateTestCasesList(testCases, -1, -1);
    }

    private void validateTestCasesList(List<AiTestCaseDraftDto> testCases, int expectedCount) {
        validateTestCasesList(testCases, expectedCount, -1);
    }

    private void validateTestCasesList(List<AiTestCaseDraftDto> testCases, int expectedCount, int questionIndex) {
        if (testCases == null || testCases.isEmpty()) {
            throw new AiIntegrationException(questionIndex > 0 ? "Câu hỏi " + questionIndex + " có test case không hợp lệ (AI trả về cấu trúc testCases không hợp lệ ở câu " + questionIndex + ")." : "AI provider returned invalid response: At least one test case is required.");
        }
        if (expectedCount > 0 && testCases.size() != expectedCount) {
            if (questionIndex > 0) {
                throw new AiIntegrationException("Câu " + questionIndex + " chỉ có " + testCases.size() + "/" + expectedCount + " test cases.");
            } else {
                throw new AiIntegrationException("Số lượng test cases (" + testCases.size() + ") không khớp với yêu cầu (" + expectedCount + ").");
            }
        }

        Set<String> names = new HashSet<>();
        Set<String> ioPairs = new HashSet<>();
        boolean hasPublic = false;

        for (AiTestCaseDraftDto tc : testCases) {
            if (tc.getName() == null || tc.getName().isBlank()) {
                throw new AiIntegrationException(questionIndex > 0 ? "Câu hỏi " + questionIndex + " có test case không hợp lệ: thiếu trường name." : "AI provider returned invalid test case: Name is required.");
            }
            String trimmedName = tc.getName().trim();
            if (trimmedName.equalsIgnoreCase("dummy") || trimmedName.equalsIgnoreCase("placeholder") || (trimmedName.equalsIgnoreCase("test") && trimmedName.length() <= 4)) {
                throw new AiIntegrationException("AI provider returned fabricated dummy test case name: " + trimmedName);
            }
            if (!names.add(trimmedName.toLowerCase(Locale.ROOT))) {
                throw new AiIntegrationException("Phát hiện test case trùng lặp tên" + (questionIndex > 0 ? " ở câu " + questionIndex + ": " : ": ") + trimmedName);
            }

            if (tc.getExpectedOutput() == null || tc.getExpectedOutput().isBlank()) {
                throw new AiIntegrationException(questionIndex > 0 ? "Câu hỏi " + questionIndex + " có test case không hợp lệ: thiếu trường expectedOutput." : "AI provider returned invalid test case: Expected output is required.");
            }

            String ioKey = (tc.getInputData() != null ? tc.getInputData().trim() : "") + "|||" + tc.getExpectedOutput().trim();
            if (!ioPairs.add(ioKey)) {
                throw new AiIntegrationException("Phát hiện test case trùng lặp nội dung input/output" + (questionIndex > 0 ? " ở câu " + questionIndex + ": " : ": ") + trimmedName);
            }

            if (Boolean.FALSE.equals(tc.getIsHidden())) {
                hasPublic = true;
            }
        }

        if (!hasPublic && !testCases.isEmpty()) {
            testCases.get(0).setIsHidden(false);
        }
    }

    private void scaleTestCasePoints(List<AiTestCaseDraftDto> testCases, BigDecimal targetTotalScore) {
        if (testCases == null || testCases.isEmpty() || targetTotalScore == null) return;
        int count = testCases.size();
        BigDecimal basePts = targetTotalScore.divide(BigDecimal.valueOf(count), 2, RoundingMode.FLOOR);
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            AiTestCaseDraftDto tc = testCases.get(i);
            if (i == count - 1) {
                BigDecimal remainder = targetTotalScore.subtract(running).setScale(2, RoundingMode.HALF_UP);
                tc.setPoints(remainder.compareTo(BigDecimal.ZERO) > 0 ? remainder : basePts);
            } else {
                tc.setPoints(basePts);
                running = running.add(basePts);
            }
        }
    }

    public void validateQuestionStructure(AiDocumentExamGenerateResponse response) {
        validateQuestionStructure(response, -1);
    }

    public void validateQuestionStructure(AiDocumentExamGenerateResponse response, int questionIndex) {
        if (response == null) {
            throw new AiIntegrationException(questionIndex > 0 ? "Câu hỏi " + questionIndex + " không có dữ liệu phản hồi từ AI." : "AI provider returned empty response.");
        }
        if (response.getTitle() == null || response.getTitle().isBlank()) {
            throw new AiIntegrationException(questionIndex > 0 ? "Câu hỏi " + questionIndex + " thiếu tiêu đề." : "AI provider returned invalid response: Missing exam title.");
        }
        if (response.getProblemStatement() == null || response.getProblemStatement().isBlank()) {
            throw new AiIntegrationException(questionIndex > 0
                    ? "AI trả về dữ liệu thiếu trường problemStatement ở câu " + questionIndex + ". Câu hỏi " + questionIndex + " thiếu nội dung đề bài. AI cần tạo lại câu hỏi này."
                    : "AI provider returned invalid response: Missing or empty problem statement.");
        }
        if (response.getInputSpecification() == null || response.getInputSpecification().isBlank()) {
            response.setInputSpecification("Dữ liệu đầu vào theo mô tả bài toán.");
        }
        if (response.getOutputSpecification() == null || response.getOutputSpecification().isBlank()) {
            response.setOutputSpecification("Kết quả đầu ra theo yêu cầu bài toán.");
        }
    }

    /**
     * Backward-compatible manual topic-based exam draft generation.
     */
    public AiExamGenerateResponse generateExamDraft(AiExamGenerateRequest request) {
        int desiredTcs = (request.getTestCasesPerQuestion() != null && request.getTestCasesPerQuestion() >= 2 && request.getTestCasesPerQuestion() <= 6)
                ? request.getTestCasesPerQuestion() : 3;
        int desiredCriteria = (request.getCriteriaCount() != null && request.getCriteriaCount() >= 1 && request.getCriteriaCount() <= 8)
                ? request.getCriteriaCount() : 4;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Please create a programming exam with the following specifications:\n")
                .append("- Topic: ").append(request.getTopic()).append("\n")
                .append("- Difficulty: ").append(request.getDifficulty()).append("\n")
                .append("- Programming Language: ").append(request.getProgrammingLanguage()).append("\n")
                .append("- Desired Criteria Count: ").append(desiredCriteria).append("\n")
                .append("- Desired Test Cases Count: EXACTLY ").append(desiredTcs).append(" test cases\n");

        if (request.getAdditionalRequirements() != null && !request.getAdditionalRequirements().isBlank()) {
            userPrompt.append("- Additional Requirements: ").append(request.getAdditionalRequirements()).append("\n");
        }

        userPrompt.append("\nReturn a structured JSON with: title, problemStatement, inputSpecification, outputSpecification, constraints, sampleInput, sampleOutput, testCases (EXACTLY ")
                .append(desiredTcs).append(" items), and rubricCriteria where sum(maxScore) equals 10.00.");

        AiExamGenerateResponse response;
        try {
            response = aiClient.generateStructuredOutput(
                    AiPromptTemplates.EXAM_GENERATION_SYSTEM,
                    userPrompt.toString(),
                    AiExamGenerateResponse.class
            );
            if (response.getProblemStatement() == null || response.getProblemStatement().isBlank()) {
                throw new AiIntegrationException("AI trả về dữ liệu thiếu trường problemStatement.");
            }
        } catch (AiTruncationException te) {
            log.warn("Topic exam generation hit truncation. Executing controlled retry...");
            response = retryGenerateExamDraft(request, desiredTcs, desiredCriteria);
        } catch (Exception ex) {
            log.warn("Topic exam generation failed or missing problemStatement ({}). Retrying with focused prompt...", ex.getMessage());
            response = retryGenerateExamDraft(request, desiredTcs, desiredCriteria);
        }

        response.setRequestedQuestionCount(request.getQuestionCount() != null ? request.getQuestionCount() : 1);
        response.setGeneratedQuestionCount(1);
        response.setRequestedTestCasesPerQuestion(desiredTcs);
        response.setGeneratedTestCasesPerQuestion(response.getTestCases() != null ? response.getTestCases().size() : 0);

        AiExamGenerateResponse singleDraftItem = AiExamGenerateResponse.builder()
                .title(response.getTitle())
                .problemStatement(response.getProblemStatement())
                .inputSpecification(response.getInputSpecification())
                .outputSpecification(response.getOutputSpecification())
                .constraints(response.getConstraints())
                .sampleInput(response.getSampleInput())
                .sampleOutput(response.getSampleOutput())
                .testCases(response.getTestCases() != null ? new ArrayList<>(response.getTestCases()) : new ArrayList<>())
                .rubricCriteria(response.getRubricCriteria() != null ? new ArrayList<>(response.getRubricCriteria()) : new ArrayList<>())
                .totalRubricScore(response.getTotalRubricScore())
                .maxScore(response.getMaxScore())
                .questions(Collections.emptyList())
                .build();
        response.setQuestions(List.of(singleDraftItem));

        validateAndNormalizeExamResponse(response);
        return response;
    }

    private AiExamGenerateResponse retryGenerateExamDraft(AiExamGenerateRequest request, int desiredTcs, int desiredCriteria) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a concise programming problem for topic '").append(request.getTopic()).append("'.\n")
                .append("Language: ").append(request.getProgrammingLanguage()).append(", Difficulty: ").append(request.getDifficulty()).append(".\n")
                .append("CRITICAL: You MUST provide a non-blank, comprehensive 'problemStatement' describing the algorithmic task and requirements.\n")
                .append("Requirements: Return valid JSON with: 'title', 'problemStatement', 'inputSpecification', 'outputSpecification', 'constraints', 'sampleInput', 'sampleOutput', 'testCases' (EXACTLY ")
                .append(desiredTcs).append(" items), and 'rubricCriteria' summing to 10.00.");

        return aiClient.generateStructuredOutput(
                AiPromptTemplates.EXAM_GENERATION_SYSTEM,
                prompt.toString(),
                AiExamGenerateResponse.class
        );
    }

    /**
     * Validates and adjusts the rubric items so that sum(maxScore) is strictly equal to 10.00.
     */
    public void validateAndNormalizeExamResponse(AiExamGenerateResponse response) {
        if (response == null) {
            throw new AiIntegrationException("AI provider returned empty response.");
        }
        if (response.getTitle() == null || response.getTitle().isBlank()) {
            throw new AiIntegrationException("AI trả về dữ liệu thiếu trường title.");
        }
        if (response.getProblemStatement() == null || response.getProblemStatement().isBlank()) {
            throw new AiIntegrationException("AI trả về dữ liệu thiếu trường problemStatement.");
        }

        if (response.getQuestions() != null && !response.getQuestions().isEmpty()) {
            int qIdx = 1;
            for (AiExamGenerateResponse subQ : response.getQuestions()) {
                if (subQ.getTitle() == null || subQ.getTitle().isBlank()) {
                    throw new AiIntegrationException("Câu hỏi " + qIdx + " thiếu tiêu đề.");
                }
                if (subQ.getProblemStatement() == null || subQ.getProblemStatement().isBlank()) {
                    throw new AiIntegrationException("AI trả về dữ liệu thiếu trường problemStatement ở câu " + qIdx + ". Câu hỏi " + qIdx + " thiếu nội dung đề bài. AI cần tạo lại câu hỏi này.");
                }
                qIdx++;
            }
        }

        if (response.getRubricCriteria() == null || response.getRubricCriteria().isEmpty()) {
            List<AiRubricItemDto> defaultCriteria = new ArrayList<>();
            defaultCriteria.add(new AiRubricItemDto("Tính đúng đắn thuật toán & Test Cases", "Chạy đúng tất cả các test cases mẫu và nâng cao", BigDecimal.valueOf(7.00)));
            defaultCriteria.add(new AiRubricItemDto("Clean Code & Tối ưu hóa", "Đặt tên biến rõ ràng, tuân thủ Clean Code và tối ưu độ phức tạp", BigDecimal.valueOf(3.00)));
            response.setRubricCriteria(defaultCriteria);
        }

        normalizeCriteriaList(response.getRubricCriteria());
        BigDecimal verifiedSum = calculateSum(response.getRubricCriteria());
        response.setTotalRubricScore(verifiedSum);

        if (response.getTestCases() != null && !response.getTestCases().isEmpty()) {
            validateTestCasesList(response.getTestCases());
            normalizeTestCasePoints(response.getTestCases());
        }
    }

    /**
     * Normalizes rubric criteria and validates test cases for Document Exam responses.
     */
    public void validateAndNormalizeDocumentExamResponse(AiDocumentExamGenerateResponse response) {
        validateQuestionStructure(response);

        if (response.getQuestions() != null && !response.getQuestions().isEmpty()) {
            int qIdx = 1;
            for (AiDocumentExamGenerateResponse subQ : response.getQuestions()) {
                validateQuestionStructure(subQ, qIdx);
                qIdx++;
            }
        }

        if (response.getRubricCriteria() == null || response.getRubricCriteria().isEmpty()) {
            List<AiRubricItemDto> defaultCriteria = new ArrayList<>();
            defaultCriteria.add(new AiRubricItemDto("Tính đúng đắn thuật toán & Test Cases", "Vượt qua toàn bộ kiểm thử mẫu và kiểm thử tự động", BigDecimal.valueOf(6.00)));
            defaultCriteria.add(new AiRubricItemDto("Cấu trúc dữ liệu & Hiệu năng", "Triển khai cấu trúc dữ liệu theo đúng tài liệu lý thuyết", BigDecimal.valueOf(4.00)));
            response.setRubricCriteria(defaultCriteria);
        }

        normalizeCriteriaList(response.getRubricCriteria());
        BigDecimal verifiedSum = calculateSum(response.getRubricCriteria());
        response.setTotalRubricScore(verifiedSum);

        if (response.getTestCases() != null && !response.getTestCases().isEmpty()) {
            validateTestCasesList(response.getTestCases());
            normalizeTestCasePoints(response.getTestCases());
        } else {
            response.setTestCases(new ArrayList<>());
        }
    }

    private void normalizeTestCasePoints(List<AiTestCaseDraftDto> testCases) {
        BigDecimal currentSum = BigDecimal.ZERO;
        for (AiTestCaseDraftDto tc : testCases) {
            if (tc.getPoints() == null || tc.getPoints().compareTo(BigDecimal.ZERO) <= 0) {
                tc.setPoints(BigDecimal.valueOf(1.00));
            }
            tc.setPoints(tc.getPoints().setScale(2, RoundingMode.HALF_UP));
            currentSum = currentSum.add(tc.getPoints());
        }

        if (currentSum.compareTo(TARGET_TOTAL_SCORE) != 0 && !testCases.isEmpty()) {
            BigDecimal runningSum = BigDecimal.ZERO;
            for (int i = 0; i < testCases.size(); i++) {
                AiTestCaseDraftDto tc = testCases.get(i);
                if (i == testCases.size() - 1) {
                    BigDecimal remainder = TARGET_TOTAL_SCORE.subtract(runningSum).setScale(2, RoundingMode.HALF_UP);
                    tc.setPoints(remainder.compareTo(BigDecimal.ZERO) > 0 ? remainder : BigDecimal.valueOf(0.50));
                } else {
                    BigDecimal scaled = tc.getPoints().multiply(TARGET_TOTAL_SCORE).divide(currentSum, 2, RoundingMode.HALF_UP);
                    tc.setPoints(scaled.compareTo(BigDecimal.ZERO) > 0 ? scaled : BigDecimal.valueOf(0.50));
                    runningSum = runningSum.add(tc.getPoints());
                }
            }
        }
    }

    private void normalizeCriteriaList(List<AiRubricItemDto> criteria) {
        BigDecimal currentSum = BigDecimal.ZERO;
        for (AiRubricItemDto item : criteria) {
            if (item.getMaxScore() == null || item.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
                item.setMaxScore(BigDecimal.valueOf(1.00));
            }
            item.setMaxScore(item.getMaxScore().setScale(2, RoundingMode.HALF_UP));
            currentSum = currentSum.add(item.getMaxScore());
        }

        if (currentSum.compareTo(TARGET_TOTAL_SCORE) != 0) {
            BigDecimal runningSum = BigDecimal.ZERO;
            for (int i = 0; i < criteria.size(); i++) {
                AiRubricItemDto item = criteria.get(i);
                if (i == criteria.size() - 1) {
                    BigDecimal remainder = TARGET_TOTAL_SCORE.subtract(runningSum).setScale(2, RoundingMode.HALF_UP);
                    if (remainder.compareTo(BigDecimal.ZERO) <= 0) {
                        remainder = BigDecimal.valueOf(0.50);
                    }
                    item.setMaxScore(remainder);
                } else {
                    BigDecimal scaled = item.getMaxScore().multiply(TARGET_TOTAL_SCORE)
                            .divide(currentSum, 2, RoundingMode.HALF_UP);
                    if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
                        scaled = BigDecimal.valueOf(0.50);
                    }
                    item.setMaxScore(scaled);
                    runningSum = runningSum.add(scaled);
                }
            }
        }
    }

    private BigDecimal calculateSum(List<AiRubricItemDto> criteria) {
        return criteria.stream()
                .map(AiRubricItemDto::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Persists teacher-confirmed exam draft with complete problem statement,
     * input/output specs, constraints, dedicated exam rubric criteria, and test cases.
     */
    public Exam saveConfirmedExam(SaveAiExamRequest request, String teacherId) {
        CourseClass courseClass = courseClassRepository.findById(request.getClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Course class not found: " + request.getClassId()));

        if (courseClass.getTeacher() == null || !courseClass.getTeacher().getId().equals(teacherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to create an exam for this class.");
        }

        // Idempotency: avoid accidental double-clicks creating twin exams within 5 seconds
        List<Exam> existingExams = examRepository.findByCourseClass_Teacher_Id(teacherId);
        LocalDateTime now = LocalDateTime.now();
        for (Exam ex : existingExams) {
            if (ex.getTitle() != null && ex.getTitle().trim().equalsIgnoreCase(request.getTitle().trim())
                    && ex.getCreatedAt() != null && ex.getCreatedAt().isAfter(now.minusSeconds(10))) {
                log.info("Idempotency: Reusing recently created exam {} for duplicate save request", ex.getId());
                return ex;
            }
        }

        Exam exam = Exam.builder()
                .title(request.getTitle())
                .courseClass(courseClass)
                .examDate(request.getExamDate())
                .duration(request.getDurationMinutes())
                .status("draft") // initial status is always draft, never auto-published
                .problemStatement(request.getProblemStatement())
                .inputSpecification(request.getInputSpecification())
                .outputSpecification(request.getOutputSpecification())
                .constraints(request.getConstraints())
                .sampleInput(request.getSampleInput())
                .sampleOutput(request.getSampleOutput())
                .programmingLanguage(request.getProgrammingLanguage() != null ? request.getProgrammingLanguage() : "Java")
                .sourceDocumentName(request.getSourceDocumentName())
                .build();
        exam.getClasses().add(courseClass);

        Exam savedExam = examRepository.save(exam);

        // Multi-Question Schema synchronization:
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            int qOrder = 0;
            for (AiDocumentExamGenerateResponse qDraft : request.getQuestions()) {
                BigDecimal qScore = qDraft.getMaxScore() != null ? qDraft.getMaxScore().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.valueOf(10.00).divide(BigDecimal.valueOf(request.getQuestions().size()), 2, RoundingMode.FLOOR);
                ExamQuestion eq = ExamQuestion.builder()
                        .exam(savedExam)
                        .title(qDraft.getTitle() != null && !qDraft.getTitle().isBlank() ? qDraft.getTitle() : "Bài toán #" + (qOrder + 1))
                        .description(qDraft.getProblemStatement())
                        .maxScore(qScore)
                        .displayOrder(qOrder++)
                        .status("DRAFT")
                        .build();
                eq = examQuestionRepository.save(eq);

                if (qDraft.getTestCases() != null && !qDraft.getTestCases().isEmpty()) {
                    int tcOrder = 0;
                    for (AiTestCaseDraftDto tc : qDraft.getTestCases()) {
                        BigDecimal tcPoints = tc.getPoints() != null ? tc.getPoints().setScale(2, RoundingMode.HALF_UP)
                                : qScore.divide(BigDecimal.valueOf(qDraft.getTestCases().size()), 2, RoundingMode.FLOOR);
                        TestCase testCase = TestCase.builder()
                                .exam(savedExam)
                                .question(eq)
                                .name(tc.getName() != null && !tc.getName().isBlank() ? tc.getName() : "Test Case #" + (tcOrder + 1))
                                .description(tc.getDescription())
                                .inputData(tc.getInputData())
                                .expectedOutput(tc.getExpectedOutput())
                                .points(tcPoints)
                                .isHidden(tc.getIsHidden() != null && tc.getIsHidden())
                                .displayOrder(tcOrder++)
                                .build();
                        testCaseRepository.save(testCase);
                    }
                }
            }
        } else {
            ExamQuestion initialQuestion = ExamQuestion.builder()
                    .exam(savedExam)
                    .title(request.getTitle())
                    .description(request.getProblemStatement())
                    .maxScore(new BigDecimal("10.00"))
                    .displayOrder(0)
                    .status("DRAFT")
                    .build();
            initialQuestion = examQuestionRepository.save(initialQuestion);

            // Save dedicated ExamRubricCriteria and sync to global ScoringCriteria
            if (request.getRubricCriteria() != null && !request.getRubricCriteria().isEmpty()) {
                int displayOrder = 0;
                for (AiRubricItemDto item : request.getRubricCriteria()) {
                    // 1. Dedicated rubric for this exam
                    ExamRubricCriteria erc = ExamRubricCriteria.builder()
                            .exam(savedExam)
                            .name(item.getName())
                            .description(item.getDescription())
                            .maxScore(item.getMaxScore())
                            .displayOrder(displayOrder++)
                            .build();
                    examRubricCriteriaRepository.save(erc);

                    // 2. Sync to global scoring_criteria for existing grading engine backward compatibility
                    ScoringCriteria sc = ScoringCriteria.builder()
                            .name(item.getName())
                            .description(item.getDescription())
                            .maxScore(item.getMaxScore())
                            .build();
                    scoringCriteriaRepository.save(sc);
                }
            }

            // Save Test Cases
            if (request.getTestCases() != null && !request.getTestCases().isEmpty()) {
                BigDecimal allocatedTcScore = BigDecimal.ZERO;
                for (AiTestCaseDraftDto tc : request.getTestCases()) {
                    BigDecimal tcPoints = tc.getPoints() != null ? tc.getPoints().setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(2.50);
                    if (tcPoints.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Điểm test case Phải lớn hơn 0");
                    }
                    if (tcPoints.compareTo(new BigDecimal("10.00")) > 0) {
                        throw new IllegalArgumentException("Điểm test case không vượt quá điểm tối đa");
                    }

                    allocatedTcScore = allocatedTcScore.add(tcPoints).setScale(2, RoundingMode.HALF_UP);
                    if (allocatedTcScore.compareTo(new BigDecimal("10.00")) > 0) {
                        throw new IllegalArgumentException("Tổng điểm các test case (" + allocatedTcScore + ") vượt quá điểm tối đa");
                    }
                }

                int order = 0;
                for (AiTestCaseDraftDto tc : request.getTestCases()) {
                    BigDecimal tcPoints = tc.getPoints() != null ? tc.getPoints().setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(2.50);
                    TestCase testCase = TestCase.builder()
                            .exam(savedExam)
                            .question(initialQuestion)
                            .name(tc.getName() != null ? tc.getName() : "Test Case #" + (order + 1))
                            .description(tc.getDescription())
                            .inputData(tc.getInputData())
                            .expectedOutput(tc.getExpectedOutput())
                            .points(tcPoints)
                            .isHidden(tc.getIsHidden() != null && tc.getIsHidden())
                            .displayOrder(order++)
                            .build();
                    testCaseRepository.save(testCase);
                }
            }
        }

        log.info("Saved AI-generated exam {} ('{}') with {} rubric items and {} test cases.",
                savedExam.getId(),
                savedExam.getTitle(),
                request.getRubricCriteria() != null ? request.getRubricCriteria().size() : 0,
                request.getTestCases() != null ? request.getTestCases().size() : 0);

        return savedExam;
    }

    private void pacingDelay(long millis) {
        if (aiClient == null || aiClient instanceof com.codeassess.ai.client.StubAiClient
                || aiClient.getClass().getName().contains("Mock") || aiClient.getClass().getName().contains("Mockito")) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
