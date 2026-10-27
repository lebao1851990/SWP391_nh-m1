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
}
