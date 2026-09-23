package com.codeassess.plagiarism.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealCreateRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.service.StudentAppealService;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/student/appeals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentAppealController {

    private final StudentAppealService studentAppealService;
    private final UserRepository userRepository;

    private User getAuthenticatedStudent() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));
    }

    @GetMapping
    public ResponseEntity<List<AppealResponseDto>> getStudentAppeals() {
        User student = getAuthenticatedStudent();
        return ResponseEntity.ok(studentAppealService.getStudentAppeals(student));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppealResponseDto> getStudentAppeal(@PathVariable("id") String opaqueId) {
        User student = getAuthenticatedStudent();
        return ResponseEntity.ok(studentAppealService.getStudentAppeal(opaqueId, student));
    }

    @PostMapping
    public ResponseEntity<AppealResponseDto> createAppeal(@Valid @RequestBody AppealCreateRequestDto dto) {
        User student = getAuthenticatedStudent();
        AppealResponseDto created = studentAppealService.createAppeal(dto, student);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
