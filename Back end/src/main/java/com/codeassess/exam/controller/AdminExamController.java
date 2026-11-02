package com.codeassess.exam.controller;

import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.AdminExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.codeassess.exam.dto.request.CreateExamDto;
import com.codeassess.exam.dto.request.UpdateExamDto;

import java.util.List;

@RestController
@RequestMapping("/api/admin/exams")
@RequiredArgsConstructor
public class AdminExamController {

    private final AdminExamService adminExamService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<ExamResponse> getAllExams() {
        return adminExamService.getAllExams();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ExamResponse getExamById(@PathVariable String id) {
        return adminExamService.getExamById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ExamResponse createExam(@Valid @RequestBody CreateExamDto dto) {
        return adminExamService.createExam(dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ExamResponse updateExam(@PathVariable String id, @Valid @RequestBody UpdateExamDto dto) {
        return adminExamService.updateExam(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteExam(@PathVariable String id) {
        adminExamService.deleteExam(id);
    }
}
