package com.codeassess.submission.controller;

import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionListResponse;
import com.codeassess.submission.service.AdminSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/submissions")
@RequiredArgsConstructor
public class AdminSubmissionController {

    private final AdminSubmissionService adminSubmissionService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<SubmissionListResponse> getAllSubmissions() {
        return adminSubmissionService.getAllSubmissions();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public SubmissionDetailResponse getSubmissionById(@PathVariable String id) {
        return adminSubmissionService.getSubmissionById(id);
    }
}
