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
     * Determines whether the examination has concluded and AI assistance is permissible.
     * 
     * Rules:
     * 1. Soft-deleted exams (deletedAt != null) are considered archived/closed -> concluded.
     * 2. Null, blank or unknown statuses -> fail-safe: not concluded (keep AI locked).
     * 3. Explicit non-concluded statuses ("draft", "upcoming", "scheduled") -> not concluded (keep AI locked).
     * 4. Explicit concluded statuses ("completed", "closed", "archived", "ended", "cancelled", "canceled") -> concluded.
     * 5. "active" status:
     *    - If examDate or duration is missing / non-positive -> fail-safe: not concluded (keep AI locked).
     *    - If now is before or exactly at deadline (now <= examDate + duration) -> not concluded (locked during live exam).
     *    - If now is strictly after deadline (now > deadline) -> concluded (unlocked post-exam).
     */
    public boolean isConcluded() {
        if (this.deletedAt != null) {
            return true;
        }

        if (this.status == null || this.status.isBlank()) {
            return false;
        }

        String normalizedStatus = this.status.trim().toLowerCase();

        if (CONCLUDED_STATUSES.contains(normalizedStatus)) {
            return true;
        }

        if ("active".equals(normalizedStatus)) {
            if (this.examDate == null || this.duration == null || this.duration <= 0) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now(APP_ZONE);
            LocalDateTime deadline = this.examDate.plusMinutes(this.duration);
            return now.isAfter(deadline);
        }

        return false;
    }
}
