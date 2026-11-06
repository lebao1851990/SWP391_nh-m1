package com.codeassess.exam.entity;

import com.codeassess.classes.entity.CourseClass;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private CourseClass courseClass;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "exam_classes",
        joinColumns = @JoinColumn(name = "exam_id"),
        inverseJoinColumns = @JoinColumn(name = "class_id")
    )
    @Builder.Default
    private java.util.Set<CourseClass> classes = new java.util.HashSet<>();

    @Column
    private String title;

    @Column(name = "exam_date")
    private LocalDateTime examDate;

    @Column
    private Integer duration;

    @Column
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "problem_statement", columnDefinition = "LONGTEXT")
    private String problemStatement;

    @Column(name = "input_specification", columnDefinition = "LONGTEXT")
    private String inputSpecification;

    @Column(name = "output_specification", columnDefinition = "LONGTEXT")
    private String outputSpecification;

    @Column(name = "constraints_text", columnDefinition = "LONGTEXT")
    private String constraints;

    @Column(name = "sample_input", columnDefinition = "LONGTEXT")
    private String sampleInput;

    @Column(name = "sample_output", columnDefinition = "LONGTEXT")
    private String sampleOutput;

    @Column(name = "programming_language")
    private String programmingLanguage;

    @Column(name = "source_document_name")
    private String sourceDocumentName;

    public static final java.time.ZoneId APP_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private static final java.util.Set<String> CONCLUDED_STATUSES = java.util.Set.of(
            "completed", "closed", "archived", "ended", "cancelled", "canceled"
    );

    /**
     * Derives the canonical runtime status of the examination based on publication state and schedule.
     * 
     * Formula:
     * endTime = startTime + durationMinutes
     * 
     * Rules:
     * - DRAFT: Exam has not been published (status is "draft", null, blank).
     * - UPCOMING: Published, current time is before scheduled start (now < startTime).
     * - ACTIVE: Published, current time is within duration (startTime <= now < endTime).
     * - COMPLETED: Published, current time is at or after scheduled end (now >= endTime), or explicitly concluded.
     */
    public String getRuntimeStatus() {
        return getRuntimeStatus(LocalDateTime.now(APP_ZONE));
    }

    public String getRuntimeStatus(LocalDateTime now) {
        if (this.deletedAt != null) {
            return "completed";
        }

        if (this.status == null || this.status.isBlank() || "draft".equalsIgnoreCase(this.status.trim())) {
            return "draft";
        }

        String normalizedStatus = this.status.trim().toLowerCase();
        if (CONCLUDED_STATUSES.contains(normalizedStatus)) {
            return "completed";
        }

        if (this.examDate == null || this.duration == null || this.duration <= 0) {
            if ("active".equalsIgnoreCase(normalizedStatus)) {
                return "active";
            }
            return "draft";
        }

        LocalDateTime startTime = this.examDate;
        LocalDateTime endTime = startTime.plusMinutes(this.duration);

        if (now.isBefore(startTime)) {
            return "upcoming";
        } else if (now.isBefore(endTime)) {
            return "active";
        } else {
            return "completed";
        }
    }

    /**
     * Determines whether the examination has concluded and AI assistance is permissible.
     */
    public boolean isConcluded() {
        return isConcluded(LocalDateTime.now(APP_ZONE));
    }

    public boolean isConcluded(LocalDateTime now) {
        return "completed".equalsIgnoreCase(getRuntimeStatus(now));
    }
}
