package com.codeassess.scoring.controller;

import com.codeassess.scoring.dto.request.ApproveGradeRequest;
import com.codeassess.scoring.dto.request.RequestRevisionRequest;
import com.codeassess.scoring.dto.response.TeacherGradingDetailResponse;
import com.codeassess.scoring.dto.response.TeacherGradingItemDto;
import com.codeassess.scoring.service.TeacherGradingService;
import com.codeassess.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/grading")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherGradingController {

    private final TeacherGradingService teacherGradingService;

    @GetMapping
    public ResponseEntity<List<TeacherGradingItemDto>> getGradingSubmissions(
            @RequestParam(value = "status", required = false) String status,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherGradingService.getTeacherGradingSubmissions(currentUser.getId(), status));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<TeacherGradingItemDto>> getPendingReviews(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherGradingService.getTeacherGradingSubmissions(currentUser.getId(), "PENDING_REVIEW"));
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<TeacherGradingDetailResponse> getGradingDetail(
            @PathVariable String submissionId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherGradingService.getGradingDetail(submissionId, currentUser.getId()));
    }

    @PostMapping("/{submissionId}/approve")
    public ResponseEntity<TeacherGradingDetailResponse> approveGrade(
            @PathVariable String submissionId,
            @RequestBody(required = false) ApproveGradeRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherGradingService.approveGrade(submissionId, currentUser.getId(), request));
    }

    @PostMapping("/{submissionId}/request-revision")
    public ResponseEntity<TeacherGradingDetailResponse> requestRevision(
            @PathVariable String submissionId,
            @Valid @RequestBody RequestRevisionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherGradingService.requestRevision(submissionId, currentUser.getId(), request));
    }

    @PostMapping("/{submissionId}/publish")
    public ResponseEntity<TeacherGradingDetailResponse> publishGrade(
            @PathVariable String submissionId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherGradingService.publishGrade(submissionId, currentUser.getId()));
    }
}

