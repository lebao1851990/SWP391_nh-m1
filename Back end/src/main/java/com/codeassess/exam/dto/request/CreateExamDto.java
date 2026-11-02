package com.codeassess.exam.dto.request;

import com.codeassess.config.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExamDto {

    @NotBlank(message = "Title is required")
    private String title;

    private String classId;
    private List<String> classIds;

    // Optional quick-creation fields for class
    private String newClassCode;
    private String newClassName;
    private String semester;

    @NotNull(message = "Exam date is required")
    @com.fasterxml.jackson.annotation.JsonAlias({"date", "examDate", "startTime"})
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime date;

    @NotNull(message = "Duration in minutes is required")
    @jakarta.validation.constraints.Min(value = 1, message = "Thời lượng bài thi phải tối thiểu 1 phút")
    @jakarta.validation.constraints.Max(value = 1440, message = "Thời lượng bài thi tối đa 1440 phút (24 giờ)")
    @com.fasterxml.jackson.annotation.JsonAlias({"duration", "durationMinutes", "timeLimit"})
    private Integer durationMinutes;
}
