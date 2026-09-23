package com.codeassess.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExamDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Class ID is required")
    private String classId;

    @NotNull(message = "Exam date is required")
    private LocalDateTime date;

    @NotNull(message = "Duration in minutes is required")
    private Integer durationMinutes;
}
