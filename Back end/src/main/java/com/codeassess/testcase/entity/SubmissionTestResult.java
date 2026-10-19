package com.codeassess.testcase.entity;

import com.codeassess.submission.entity.Submission;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "submission_test_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id")
    private TestCase testCase;

    @Column(name = "test_case_name")
    private String testCaseName;

    @Column(name = "is_hidden", nullable = false, columnDefinition = "TINYINT")
    @Builder.Default
    private Boolean isHidden = false;

    @Column(nullable = false, length = 50)
    private String status; // PASSED, FAILED, COMPILE_ERROR, TIMEOUT, RUNTIME_ERROR

    @Column(name = "actual_output", columnDefinition = "LONGTEXT")
    private String actualOutput;

    @Column(name = "expected_output", columnDefinition = "LONGTEXT")
    private String expectedOutput;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "points_awarded", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal pointsAwarded = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
