package com.codeassess.exam.dto.request;

import com.codeassess.config.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255, message = "Tiêu đề bài thi không được vượt quá 255 ký tự")
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
    @Min(value = 1, message = "Thời lượng bài thi phải tối thiểu 1 phút")
    @Max(value = 1440, message = "Thời lượng bài thi tối đa 1440 phút (24 giờ)")
    @com.fasterxml.jackson.annotation.JsonAlias({"duration", "durationMinutes", "timeLimit"})
    private Integer durationMinutes;

    @Pattern(regexp = "^(?i)(Java|C\\+\\+|Python|JavaScript)$", message = "Ngôn ngữ lập trình không hợp lệ. Chỉ chấp nhận: Java, C++, Python, JavaScript")
    private String programmingLanguage;

    @Pattern(regexp = "^(?i)(draft|upcoming|active|completed|published)$", message = "Trạng thái bài thi không hợp lệ")
    private String status;

    private List<ExamQuestionDraftDto> questions;
}
