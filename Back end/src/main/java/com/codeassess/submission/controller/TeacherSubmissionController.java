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

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}/audit-logs")
    public List<com.codeassess.scoring.dto.response.GradeAuditLogDto> getAuditLogs(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return teacherScoringService.getAuditLogs(id, currentUser.getId());
    }
}
