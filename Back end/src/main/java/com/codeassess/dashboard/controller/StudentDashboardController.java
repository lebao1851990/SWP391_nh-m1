package com.codeassess.dashboard.controller;

import com.codeassess.dashboard.dto.StudentDashboardDto;
import com.codeassess.dashboard.service.StudentDashboardService;
import com.codeassess.user.entity.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/dashboard")
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;

    public StudentDashboardController(StudentDashboardService studentDashboardService) {
        this.studentDashboardService = studentDashboardService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping
    public StudentDashboardDto getDashboard(@AuthenticationPrincipal User currentUser) {
        return studentDashboardService.getDashboard(currentUser.getId());
    }
}
