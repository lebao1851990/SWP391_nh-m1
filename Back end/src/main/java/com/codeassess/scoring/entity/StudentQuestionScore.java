package com.codeassess.scoring.entity;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "student_question_scores",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sqs_question_student", columnNames = {"question_id", "student_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentQuestionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ExamQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "latest_submission_id")
    private String latestSubmissionId;

    @Column(name = "latest_submission_seq", nullable = false)
    @Builder.Default
    private Integer latestSubmissionSeq = 1;

    @Column(name = "latest_status", nullable = false, length = 20)
    @Builder.Default
    private String latestStatus = "PENDING";

    @Column(name = "submission_count", nullable = false)
    @Builder.Default
    private Integer submissionCount = 1;

    @Column(name = "effective_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal effectiveScore = BigDecimal.ZERO;

    @Column(name = "auto_score", precision = 5, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "override_score", precision = 5, scale = 2)
    private BigDecimal overrideScore;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "overridden_by")
    private String overriddenBy;

    @Column(name = "overridden_at")
    private LocalDateTime overriddenAt;

    @Column(name = "last_submitted_at")
    private LocalDateTime lastSubmittedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

