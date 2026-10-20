package com.codeassess.exam.dto.request;

import com.codeassess.config.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

    @com.fasterxml.jackson.annotation.JsonAlias({"date", "examDate", "startTime"})
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime date;

    @jakarta.validation.constraints.Min(value = 1, message = "Thời lượng bài thi phải tối thiểu 1 phút")
    @jakarta.validation.constraints.Max(value = 1440, message = "Thời lượng bài thi tối đa 1440 phút (24 giờ)")
    @com.fasterxml.jackson.annotation.JsonAlias({"duration", "durationMinutes", "timeLimit"})
    private Integer durationMinutes;
    
    @Pattern(regexp = "^(?i)(upcoming|active|completed)$", message = "Status must be upcoming, active, or completed")
    private String status;
}
