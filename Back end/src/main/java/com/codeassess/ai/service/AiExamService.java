package com.codeassess.ai.service;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.dto.*;
import com.codeassess.ai.prompt.AiPromptTemplates;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
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
import java.util.ArrayList;
import java.util.List;

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
     * Stage 2 & 3: Document Exam Generation Agent.
     * Reads document content and user constraints, and synthesizes a full exam problem with test cases and rubric.
     */
    public AiDocumentExamGenerateResponse generateExamFromDocument(
            MultipartFile file,
            String difficulty,
            String programmingLanguage,
            Integer criteriaCount,
            String additionalRequirements) {

        DocumentExtractorService.ExtractedDocument doc = documentExtractorService.extractText(file);

        String lang = (programmingLanguage != null && !programmingLanguage.isBlank()) ? programmingLanguage.trim() : "Java";
        String diff = (difficulty != null && !difficulty.isBlank()) ? difficulty.trim().toUpperCase() : "MEDIUM";
        int desiredCriteria = (criteriaCount != null && criteriaCount >= 2 && criteriaCount <= 6) ? criteriaCount : 4;

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
                .append("3. 'testCases' (2 to 4 items with realistic inputs/outputs and point weights summing to 10.00).\n")
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

        validateAndNormalizeDocumentExamResponse(response);
        return response;
    }

    /**
     * Backward-compatible manual topic-based exam draft generation.
     */
    public AiExamGenerateResponse generateExamDraft(AiExamGenerateRequest request) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Please create a programming exam with the following specifications:\n")
                .append("- Topic: ").append(request.getTopic()).append("\n")
                .append("- Difficulty: ").append(request.getDifficulty()).append("\n")
                .append("- Programming Language: ").append(request.getProgrammingLanguage()).append("\n")
                .append("- Desired Criteria Count: ").append(request.getCriteriaCount() != null ? request.getCriteriaCount() : 4).append("\n");

        if (request.getAdditionalRequirements() != null && !request.getAdditionalRequirements().isBlank()) {
            userPrompt.append("- Additional Requirements: ").append(request.getAdditionalRequirements()).append("\n");
        }

        userPrompt.append("\nReturn a structured JSON with: title, problemStatement, inputSpecification, outputSpecification, constraints, sampleInput, sampleOutput, testCases (2-4 items), and rubricCriteria where sum(maxScore) equals 10.00.");

        AiExamGenerateResponse response = aiClient.generateStructuredOutput(
                AiPromptTemplates.EXAM_GENERATION_SYSTEM,
                userPrompt.toString(),
                AiExamGenerateResponse.class
        );

        validateAndNormalizeExamResponse(response);
        return response;
    }

    /**
     * Validates and adjusts the rubric items so that sum(maxScore) is strictly equal to 10.00.
     */
    public void validateAndNormalizeExamResponse(AiExamGenerateResponse response) {
        if (response.getRubricCriteria() == null || response.getRubricCriteria().isEmpty()) {
            List<AiRubricItemDto> defaultCriteria = new ArrayList<>();
            defaultCriteria.add(new AiRubricItemDto("Tính đúng đắn thuật toán & Test Cases", "Chạy đúng tất cả các test cases mẫu và nâng cao", BigDecimal.valueOf(7.00)));
            defaultCriteria.add(new AiRubricItemDto("Clean Code & Tối ưu hóa", "Đặt tên biến rõ ràng, tuân thủ Clean Code và tối ưu độ phức tạp", BigDecimal.valueOf(3.00)));
            response.setRubricCriteria(defaultCriteria);
        }

        normalizeCriteriaList(response.getRubricCriteria());
        BigDecimal verifiedSum = calculateSum(response.getRubricCriteria());
        response.setTotalRubricScore(verifiedSum);
    }

    /**
     * Normalizes rubric criteria for Document Exam responses.
     */
    public void validateAndNormalizeDocumentExamResponse(AiDocumentExamGenerateResponse response) {
        if (response.getRubricCriteria() == null || response.getRubricCriteria().isEmpty()) {
            List<AiRubricItemDto> defaultCriteria = new ArrayList<>();
            defaultCriteria.add(new AiRubricItemDto("Tính đúng đắn thuật toán & Test Cases", "Vượt qua toàn bộ kiểm thử mẫu và kiểm thử tự động", BigDecimal.valueOf(6.00)));
            defaultCriteria.add(new AiRubricItemDto("Cấu trúc dữ liệu & Hiệu năng", "Triển khai cấu trúc dữ liệu theo đúng tài liệu lý thuyết", BigDecimal.valueOf(4.00)));
            response.setRubricCriteria(defaultCriteria);
        }

        normalizeCriteriaList(response.getRubricCriteria());
        BigDecimal verifiedSum = calculateSum(response.getRubricCriteria());
        response.setTotalRubricScore(verifiedSum);

        // Ensure at least 2 test cases exist
        if (response.getTestCases() == null || response.getTestCases().isEmpty()) {
            List<AiTestCaseDraftDto> defaultTests = new ArrayList<>();
            defaultTests.add(AiTestCaseDraftDto.builder()
                    .name("Test cơ bản 1")
                    .description("Kiểm thử dữ liệu mẫu")
                    .inputData(response.getSampleInput() != null ? response.getSampleInput() : "1")
                    .expectedOutput(response.getSampleOutput() != null ? response.getSampleOutput() : "1")
                    .points(BigDecimal.valueOf(5.00))
                    .isHidden(false)
                    .build());
            defaultTests.add(AiTestCaseDraftDto.builder()
                    .name("Test nâng cao 2")
                    .description("Kiểm thử dữ liệu biên")
                    .inputData("0")
                    .expectedOutput("0")
                    .points(BigDecimal.valueOf(5.00))
                    .isHidden(true)
                    .build());
            response.setTestCases(defaultTests);
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
            int order = 0;
            for (AiTestCaseDraftDto tc : request.getTestCases()) {
                TestCase testCase = TestCase.builder()
                        .exam(savedExam)
                        .name(tc.getName() != null ? tc.getName() : "Test Case #" + (order + 1))
                        .description(tc.getDescription())
                        .inputData(tc.getInputData())
                        .expectedOutput(tc.getExpectedOutput())
                        .points(tc.getPoints() != null ? tc.getPoints() : BigDecimal.valueOf(2.50))
                        .isHidden(tc.getIsHidden() != null && tc.getIsHidden())
                        .displayOrder(order++)
                        .build();
                testCaseRepository.save(testCase);
            }
        }

        log.info("Saved AI-generated exam {} ('{}') with {} rubric items and {} test cases.",
                savedExam.getId(),
                savedExam.getTitle(),
                request.getRubricCriteria() != null ? request.getRubricCriteria().size() : 0,
                request.getTestCases() != null ? request.getTestCases().size() : 0);

        return savedExam;
    }
}
