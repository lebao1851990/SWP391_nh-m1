package com.codeassess.scoring.controller;

import com.codeassess.scoring.dto.response.StudentSubmissionResult;
import com.codeassess.scoring.service.StudentScoringService;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/submissions/{id}/result")
@RequiredArgsConstructor
public class StudentScoringController {

    private final StudentScoringService studentScoringService;

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping
    public StudentSubmissionResult getResult(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return studentScoringService.getStudentResult(id, currentUser.getId());
    }
}
