package com.codeassess.submission.controller;

import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.TeacherSubmissionListResponse;
import com.codeassess.submission.service.TeacherSubmissionService;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/submissions")
@RequiredArgsConstructor
public class TeacherSubmissionController {

    private final TeacherSubmissionService teacherSubmissionService;
    private final com.codeassess.scoring.service.TeacherScoringService teacherScoringService;

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping
    public List<TeacherSubmissionListResponse> getMySubmissions(@AuthenticationPrincipal User currentUser) {
        return teacherSubmissionService.getTeacherSubmissions(currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}")
    public SubmissionDetailResponse getSubmissionById(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return teacherSubmissionService.getTeacherSubmissionById(id, currentUser.getId());
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping("/{id}/audit-logs")
    public List<com.codeassess.scoring.dto.response.GradeAuditLogDto> getAuditLogs(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return teacherScoringService.getAuditLogs(id, currentUser);
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}/download")
    public org.springframework.http.ResponseEntity<byte[]> downloadSubmissionZip(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        byte[] zipBytes = teacherSubmissionService.getSubmissionZip(id, currentUser.getId());
        SubmissionDetailResponse detail = teacherSubmissionService.getTeacherSubmissionById(id, currentUser.getId());
        String filename = detail.getOriginalFilename() != null && !detail.getOriginalFilename().isBlank()
                ? detail.getOriginalFilename()
                : "submission-" + id + ".zip";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}/files")
    public List<com.codeassess.submission.dto.response.SubmissionFileDto> getSubmissionFiles(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        return teacherSubmissionService.getSubmissionFiles(id, currentUser.getId());
    }

    public record FeedbackRequest(String feedback) {}

    @PreAuthorize("hasRole('TEACHER')")
    @org.springframework.web.bind.annotation.PutMapping("/{id}/feedback")
    public org.springframework.http.ResponseEntity<Void> updateFeedback(
            @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody FeedbackRequest request,
            @AuthenticationPrincipal User currentUser) {
        teacherSubmissionService.saveInstructorFeedback(id, currentUser.getId(), request != null ? request.feedback() : null);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
