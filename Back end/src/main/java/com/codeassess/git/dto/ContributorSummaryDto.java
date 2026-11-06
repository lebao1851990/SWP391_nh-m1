package com.codeassess.git.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributorSummaryDto {
    private String gitAuthorEmail;
    private String gitAuthorName;
    private String userId;
    private String userFullName;
    private boolean isMapped;

    // Raw factual counts
    private int totalCommits;
    private int nonMergeCommits;
    private int mergeCommits;
    private int revertCommits;
    private int linesAdded;
    private int linesDeleted;
    private int netLines;
    private int filesChanged;
    private int activeDays;

    private Instant firstCommitAt;
    private Instant lastCommitAt;

    // Relative distribution (%)
    private double commitPercentage;
    private double lineAdditionPercentage;

    // Balanced contribution indicators (transparent, non-grade)
    private double consistencyRatio; // active days / total active project days
    private double churnBalanceRatio; // deletions / additions
}

