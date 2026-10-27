package com.codeassess.ai.controller;

import com.codeassess.ai.dto.*;
import com.codeassess.ai.service.AiExamService;
import com.codeassess.exam.entity.Exam;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/teacher/exams/ai")
@RequiredArgsConstructor
public class TeacherAiExamController {

    private final AiExamService aiExamService;
    private final UserRepository userRepository;

    private String resolveTeacherId(User currentUser) {
        if (currentUser != null && currentUser.getId() != null) {
            return currentUser.getId();
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmail(auth.getName())
                    .map(User::getId)
                    .orElse(auth.getName());
        }
        throw new com.codeassess.common.exception.ResourceNotFoundException("Authenticated teacher not found");
    }

    /**
     * Stage 1: Uploads document and returns structured analysis (main topic, sub-topics, key concepts, snippets).
     */
    @PostMapping(value = "/analyze-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<DocumentAnalysisResponse> analyzeUploadedDocument(
            @RequestParam("file") MultipartFile file) {
        log.info("Teacher requested document analysis for file '{}' ({} bytes)",
                file.getOriginalFilename(), file.getSize());
        DocumentAnalysisResponse response = aiExamService.analyzeDocument(file);
        return ResponseEntity.ok(response);
    }

    /**
     * Stage 2 & 3: Uploads document and synthesizes a full exam draft grounded in the document.
     */
    @PostMapping(value = "/generate-from-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AiDocumentExamGenerateResponse> generateExamFromDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "difficulty", required = false, defaultValue = "MEDIUM") String difficulty,
            @RequestParam(value = "programmingLanguage", required = false, defaultValue = "Java") String programmingLanguage,
            @RequestParam(value = "criteriaCount", required = false, defaultValue = "4") Integer criteriaCount,
            @RequestParam(value = "additionalRequirements", required = false) String additionalRequirements) {
        log.info("Teacher requested AI Document Exam Generation for file '{}', difficulty='{}', lang='{}'",
                file.getOriginalFilename(), difficulty, programmingLanguage);

        AiDocumentExamGenerateResponse response = aiExamService.generateExamFromDocument(
                file,
                difficulty,
                programmingLanguage,
                criteriaCount,
                additionalRequirements
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Backward-compatible manual topic-based draft generator.
     */
    @PostMapping("/generate-draft")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AiExamGenerateResponse> generateExamDraft(
            @Valid @RequestBody AiExamGenerateRequest request) {
        AiExamGenerateResponse draft = aiExamService.generateExamDraft(request);
        return ResponseEntity.ok(draft);
    }

    /**
     * Confirms and persists the exam with full problem statement, test cases, and dedicated rubric criteria.
     */
    @PostMapping("/confirm-save")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Map<String, Object>> saveConfirmedExam(
            @Valid @RequestBody SaveAiExamRequest request,
            @AuthenticationPrincipal User currentUser) {
        String teacherId = resolveTeacherId(currentUser);
        Exam savedExam = aiExamService.saveConfirmedExam(request, teacherId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "examId", savedExam.getId(),
                "title", savedExam.getTitle(),
                "status", savedExam.getStatus(),
                "message", "Bài thi đã được lưu thành công ở trạng thái bản nháp (draft)."
        ));
    }
}
