package com.codeassess.scoring.entity;

import com.codeassess.submission.entity.Submission;
import com.codeassess.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "pre_rubric_score", precision = 5, scale = 2)
    private BigDecimal preRubricScore;

    @Column(name = "post_rubric_score", precision = 5, scale = 2)
    private BigDecimal postRubricScore;

    @Column(name = "pre_override_score", precision = 5, scale = 2)
    private BigDecimal preOverrideScore;

    @Column(name = "post_override_score", precision = 5, scale = 2)
    private BigDecimal postOverrideScore;

    @Column(name = "pre_effective_score", precision = 5, scale = 2)
    private BigDecimal preEffectiveScore;

    @Column(name = "post_effective_score", precision = 5, scale = 2)
    private BigDecimal postEffectiveScore;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
