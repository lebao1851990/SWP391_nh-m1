package com.codeassess.submission.entity;

import com.codeassess.exam.entity.Exam;
import com.codeassess.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(length = 50)
    private String status;

    @Column(name = "source_code", columnDefinition = "LONGTEXT")
    @Lob
    private String sourceCode;

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "override_score", precision = 5, scale = 2)
    private BigDecimal overrideScore;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Builder.Default
    @Column(name = "submission_type", length = 20)
    private String submissionType = "SINGLE_FILE";

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "archive_hash", length = 64)
    private String archiveHash;

    @Column(name = "source_tree_hash", length = 64)
    private String sourceTreeHash;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "entry_point_class")
    private String entryPointClass;

    @Column(name = "execution_status", length = 50)
    private String executionStatus;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "compile_output", columnDefinition = "TEXT")
    private String compileOutput;

    @Column(name = "stdout", columnDefinition = "TEXT")
    private String stdout;

    @Column(name = "stderr", columnDefinition = "TEXT")
    private String stderr;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
