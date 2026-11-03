package com.codeassess.scoring.controller;

import com.codeassess.scoring.dto.request.QuestionOverrideRequestDto;
import com.codeassess.scoring.dto.response.ExamScoreAggregationDto;
import com.codeassess.scoring.dto.response.QuestionScoreDetailDto;
import com.codeassess.scoring.service.ExamScoreAggregationService;
import com.codeassess.scoring.service.QuestionScoreOverrideService;
import com.codeassess.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/exams/{examId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherExamScoringController {

    private final QuestionScoreOverrideService questionScoreOverrideService;
    private final ExamScoreAggregationService examScoreAggregationService;

    @GetMapping("/students/{studentId}/scores")
    public ResponseEntity<ExamScoreAggregationDto> getStudentExamScores(
            @PathVariable String examId,
            @PathVariable String studentId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(examScoreAggregationService.calculateExamScore(examId, studentId, currentUser.getId()));
    }

    @PutMapping("/questions/{questionId}/students/{studentId}/override")
    public ResponseEntity<QuestionScoreDetailDto> setQuestionOverride(
            @PathVariable String examId,
            @PathVariable String questionId,
            @PathVariable String studentId,
            @Valid @RequestBody QuestionOverrideRequestDto request,
            @AuthenticationPrincipal User currentUser) {
        QuestionScoreDetailDto detail = questionScoreOverrideService.setQuestionOverride(
                examId, questionId, studentId, request, currentUser.getId());
        return ResponseEntity.ok(detail);
    }

    @DeleteMapping("/questions/{questionId}/students/{studentId}/override")
    public ResponseEntity<QuestionScoreDetailDto> clearQuestionOverride(
            @PathVariable String examId,
            @PathVariable String questionId,
            @PathVariable String studentId,
            @AuthenticationPrincipal User currentUser) {
        QuestionScoreDetailDto detail = questionScoreOverrideService.clearQuestionOverride(
                examId, questionId, studentId, currentUser.getId());
        return ResponseEntity.ok(detail);
    }
}

