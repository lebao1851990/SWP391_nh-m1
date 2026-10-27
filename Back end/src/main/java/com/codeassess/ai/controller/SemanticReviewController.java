package com.codeassess.ai.controller;

import com.codeassess.ai.dto.SemanticReviewResponse;
import com.codeassess.ai.service.SemanticReviewService;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SemanticReviewController {

    private final SemanticReviewService semanticReviewService;
    private final UserRepository userRepository;

    private String resolveUserId(User currentUser) {
        if (currentUser != null && currentUser.getId() != null) {
            return currentUser.getId();
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmail(auth.getName())
                    .map(User::getId)
                    .orElse(auth.getName());
        }
        throw new com.codeassess.common.exception.ResourceNotFoundException("Authenticated user not found");
    }

    @PostMapping("/student/submissions/{id}/ai/semantic-review")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SemanticReviewResponse> getStudentSemanticReview(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        String studentId = resolveUserId(currentUser);
        SemanticReviewResponse response = semanticReviewService.reviewForStudent(id, studentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/teacher/submissions/{id}/ai/semantic-review")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<SemanticReviewResponse> getTeacherSemanticReview(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        String teacherId = resolveUserId(currentUser);
        SemanticReviewResponse response = semanticReviewService.reviewForTeacher(id, teacherId);
        return ResponseEntity.ok(response);
    }
}
