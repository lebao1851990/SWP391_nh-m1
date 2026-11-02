package com.codeassess.exam.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ExamResponse {
    private String id;
    private String classId;
    private String title;
    private LocalDateTime examDate;
    private Integer duration;
    private String status;
    private LocalDateTime createdAt;
    
    // Additional fields for detail view
    private String courseName;
    private String courseCode;
    private Integer enrolledCount;
    private Integer submittedCount;

    // Multi-class support
    private java.util.List<String> classIds;
    private java.util.List<String> classCodes;
    private java.util.List<String> classNames;
    private java.util.List<ClassSummaryDto> assignedClasses;

    // Time tracking & auto-lock metadata
    private LocalDateTime endTime;
    private LocalDateTime serverTime;
    private Boolean isExpired;

    // AI & Problem Specifications
    private String problemStatement;
    private String inputSpecification;
    private String outputSpecification;
    private String constraints;
    private String sampleInput;
    private String sampleOutput;
    private String programmingLanguage;
    private String sourceDocumentName;
    private java.util.List<com.codeassess.ai.dto.AiRubricItemDto> rubricCriteria;

    @com.fasterxml.jackson.annotation.JsonProperty("durationMinutes")
    public Integer getDurationMinutes() {
        return duration;
    }
}
