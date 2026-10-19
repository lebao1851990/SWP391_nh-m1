package com.codeassess.plagiarism.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "plagiarism_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlagiarismEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "similarity_matrix_id", nullable = false)
    private SimilarityPair similarityPair;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "submission_a_start_line")
    private Integer startLineA;

    @Column(name = "submission_a_end_line")
    private Integer endLineA;

    @Column(name = "submission_b_start_line")
    private Integer startLineB;

    @Column(name = "submission_b_end_line")
    private Integer endLineB;

    @Column(name = "submission_a_location")
    private String locationA;

    @Column(name = "submission_b_location")
    private String locationB;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
