package com.codeassess.exam.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExamDto {
    private String title;
    private String classId;
    private java.util.List<String> classIds;
    private LocalDateTime date;
    private Integer durationMinutes;
    
    @Pattern(regexp = "^(?i)(upcoming|active|completed)$", message = "Status must be upcoming, active, or completed")
    private String status;
}
