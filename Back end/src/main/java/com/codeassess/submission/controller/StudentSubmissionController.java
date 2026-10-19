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
    @GetMapping("/submissions/{id}")
    public SubmissionDetailResponse getSubmissionById(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return studentSubmissionService.getStudentSubmissionById(id, currentUser.getId());
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
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        SubmissionDetailResponse response = studentSubmissionService.submitZip(examId, currentUser.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
