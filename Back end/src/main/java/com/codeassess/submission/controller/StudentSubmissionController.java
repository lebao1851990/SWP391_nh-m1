package com.codeassess.submission.controller;

import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionListResponse;
import com.codeassess.submission.service.StudentSubmissionService;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentSubmissionController {

    private final StudentSubmissionService studentSubmissionService;

    public StudentSubmissionController(StudentSubmissionService studentSubmissionService) {
        this.studentSubmissionService = studentSubmissionService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/submissions")
    public List<SubmissionListResponse> getMySubmissions(@AuthenticationPrincipal User currentUser) {
        return studentSubmissionService.getStudentSubmissions(currentUser.getId());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/exams/{examId}/submissions")
    public List<SubmissionDetailResponse> getExamSubmissions(
            @PathVariable String examId,
            @AuthenticationPrincipal User currentUser) {
        return studentSubmissionService.getStudentExamSubmissions(examId, currentUser.getId());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/submissions/{id}")
    public SubmissionDetailResponse getSubmissionById(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return studentSubmissionService.getStudentSubmissionById(id, currentUser.getId());
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.scoring.service.StudentScoringService studentScoringService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.codeassess.plagiarism.service.StudentAppealService studentAppealService;

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/submissions/{id}/published-grade")
    public com.codeassess.scoring.dto.response.StudentSubmissionResult getPublishedGrade(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        if (studentScoringService == null) {
            throw new com.codeassess.common.exception.ResourceNotFoundException("Scoring service not available");
        }
        return studentScoringService.getStudentResult(id, currentUser.getId());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/submissions/{id}/appeals")
    public ResponseEntity<com.codeassess.plagiarism.dto.AppealResponseDto> createSubmissionAppeal(
            @PathVariable String id,
            @Valid @RequestBody com.codeassess.plagiarism.dto.AppealCreateRequestDto dto,
            @AuthenticationPrincipal User currentUser) {
        if (studentAppealService == null) {
            throw new com.codeassess.common.exception.ResourceNotFoundException("Appeal service not available");
        }
        dto.setSubmissionId(id);
        com.codeassess.plagiarism.dto.AppealResponseDto response = studentAppealService.createAppeal(dto, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/submissions/{id}/download")
    public ResponseEntity<byte[]> downloadSubmissionZip(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        byte[] zipBytes = studentSubmissionService.getSubmissionZip(id, currentUser.getId());
        SubmissionDetailResponse detail = studentSubmissionService.getStudentSubmissionById(id, currentUser.getId());
        String filename = detail.getOriginalFilename() != null && !detail.getOriginalFilename().isBlank()
                ? detail.getOriginalFilename()
                : "submission-" + id + ".zip";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/submissions/{id}/files")
    public List<com.codeassess.submission.dto.response.SubmissionFileDto> getSubmissionFiles(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        return studentSubmissionService.getSubmissionFiles(id, currentUser.getId());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(value = "/exams/{examId}/submissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SubmissionDetailResponse> submitCode(
            @PathVariable String examId,
            @Valid @RequestBody SubmissionCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        SubmissionDetailResponse response = studentSubmissionService.submitCode(examId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(value = "/exams/{examId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionDetailResponse> submitZip(
            @PathVariable String examId,
            @RequestParam(value = "questionId", required = false) String questionId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        SubmissionDetailResponse response = studentSubmissionService.submitZip(examId, questionId, currentUser.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(value = "/exams/{examId}/submissions/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<SubmissionDetailResponse>> submitBatchZip(
            @PathVariable String examId,
            @RequestParam("questionIds") List<String> questionIds,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal User currentUser) {
        List<SubmissionDetailResponse> responses = studentSubmissionService.submitBatchZip(
                examId, currentUser.getId(), questionIds, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }
}

