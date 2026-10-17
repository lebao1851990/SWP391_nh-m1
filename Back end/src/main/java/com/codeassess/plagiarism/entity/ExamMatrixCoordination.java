package com.codeassess.plagiarism.entity;

import com.codeassess.exam.entity.Exam;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_matrix_coordination")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamMatrixCoordination {

    @Id
    @Column(name = "exam_id", length = 36)
    private String examId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Builder.Default
    @Column(name = "current_generation", nullable = false)
    private Long currentGeneration = 0L;

    @Builder.Default
    @Column(name = "requested_generation", nullable = false)
    private Long requestedGeneration = 0L;

    @Builder.Default
    @Column(name = "locked_generation", nullable = false)
    private Long lockedGeneration = 0L;

    @Column(name = "lease_token", length = 36)
    private String leaseToken;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Builder.Default
    @Column(name = "status", length = 30, nullable = false)
    private String status = "IDLE";

    @Builder.Default
    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
