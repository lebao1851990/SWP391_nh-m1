package com.codeassess.scoring.controller;

import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.dto.response.ScoringDetail;
import com.codeassess.scoring.service.TeacherScoringService;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/submissions/{id}/scoring")
@RequiredArgsConstructor
public class TeacherScoringController {

    private final TeacherScoringService teacherScoringService;

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping
    public ScoringDetail getScoring(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return teacherScoringService.getScoringDetail(id, currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping
    public ScoringDetail updateScoring(@PathVariable String id, @jakarta.validation.Valid @RequestBody SaveScoringPayload payload, @AuthenticationPrincipal User currentUser) {
        return teacherScoringService.updateScoring(id, currentUser.getId(), payload);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping("/audit-logs")
    public java.util.List<com.codeassess.scoring.dto.response.GradeAuditLogDto> getAuditLogs(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return teacherScoringService.getAuditLogs(id, currentUser);
    }
}
