package com.codeassess.ai.controller;

import com.codeassess.ai.dto.StudentAiMode;
import com.codeassess.ai.dto.StudentAiRequest;
import com.codeassess.ai.dto.StudentAiResponse;
import com.codeassess.ai.dto.StudentSandboxAiRequest;
import com.codeassess.ai.service.StudentAiService;
import com.codeassess.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class StudentAiController {

    private final StudentAiService studentAiService;
    private final com.codeassess.user.repository.UserRepository userRepository;

    public StudentAiController(StudentAiService studentAiService, com.codeassess.user.repository.UserRepository userRepository) {
        this.studentAiService = studentAiService;
        this.userRepository = userRepository;
    }

    private String resolveStudentId(User currentUser) {
        if (currentUser != null && currentUser.getId() != null) {
            return currentUser.getId();
        }
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmail(auth.getName())
                    .map(User::getId)
                    .orElse(auth.getName());
        }
        throw new com.codeassess.common.exception.ResourceNotFoundException("Authenticated student user not found");
    }

    @PostMapping("/submissions/{id}/ai/assist")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentAiResponse> getSubmissionFeedback(
            @PathVariable String id,
            @Valid @RequestBody StudentAiRequest request,
            @AuthenticationPrincipal User currentUser) {
        String studentId = resolveStudentId(currentUser);
        StudentAiResponse response = studentAiService.getSubmissionFeedback(id, studentId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submissions/{id}/ai/explain-error")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentAiResponse> explainSubmissionError(
            @PathVariable String id,
            @RequestBody(required = false) StudentAiRequest request,
            @AuthenticationPrincipal User currentUser) {
        String studentId = resolveStudentId(currentUser);
        StudentAiRequest actualRequest = (request != null && request.compilerOutput() != null)
                ? new StudentAiRequest(StudentAiMode.COMPILER_ERROR, request.compilerOutput())
                : new StudentAiRequest(StudentAiMode.COMPILER_ERROR, null);

        StudentAiResponse response = studentAiService.getSubmissionFeedback(id, studentId, actualRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sandbox/ai/explain")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentAiResponse> getSandboxFeedback(
            @Valid @RequestBody StudentSandboxAiRequest request,
            @AuthenticationPrincipal User currentUser) {
        String studentId = resolveStudentId(currentUser);
        StudentAiResponse response = studentAiService.getSandboxFeedback(studentId, request);
        return ResponseEntity.ok(response);
    }
}
