package com.codeassess.admin.controller;

import com.codeassess.admin.dto.response.AdminDashboardMetricsDto;
import com.codeassess.admin.service.AdminMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public AdminDashboardMetricsDto getMetrics() {
        return adminMetricsService.getMetrics();
    }
}
