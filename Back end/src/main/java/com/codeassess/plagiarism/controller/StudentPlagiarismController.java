package com.codeassess.plagiarism.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.StudentPlagiarismDetailDto;

import com.codeassess.plagiarism.service.StudentPlagiarismService;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/plagiarism")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentPlagiarismController {

    private final StudentPlagiarismService plagiarismService;
    private final UserRepository userRepository;

    private User getAuthenticatedStudent() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentPlagiarismDetailDto> getPlagiarismDetail(@PathVariable Long id) {
        User student = getAuthenticatedStudent();
        return ResponseEntity.ok(plagiarismService.getPlagiarismDetail(id, student));
    }
}
