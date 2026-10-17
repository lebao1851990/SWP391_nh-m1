package com.codeassess.plagiarism.entity;

import com.codeassess.exam.entity.Exam;
import com.codeassess.submission.entity.Submission;
import jakarta.persistence.*;
import lombok.*;
import com.codeassess.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "similarity_matrix")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_a_id", nullable = false)
    private Submission submissionA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_b_id", nullable = false)
    private Submission submissionB;

    @Column(name = "similarity_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal similarityRate;

    @Column(name = "shared_hashes_count")
    private Integer sharedHashesCount;

    @Column(name = "is_flagged", columnDefinition = "TINYINT")
    private Boolean isFlagged;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(name = "adjudication_status", length = 30)
    private String adjudicationStatus = "PENDING";

    @Column(name = "adjudication_notes", columnDefinition = "TEXT")
    private String adjudicationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjudicated_by_id")
    private User adjudicatedBy;

    @Column(name = "adjudicated_at")
    private LocalDateTime adjudicatedAt;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
