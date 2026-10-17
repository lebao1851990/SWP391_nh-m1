package com.codeassess.plagiarism.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppealResponseDto {
    private String id;
    private String submissionId;
    private String examName;
    private String status;
    private String basis;
    private String explanation;
    private String studentName;
    private String studentEmail;
    private String reviewerNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
