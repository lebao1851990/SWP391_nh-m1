package com.codeassess.git.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryAnalyticsResponseDto {
    private String repositoryId;
    private String repositoryName;
    private String repoUrl;
    private String defaultBranch;
    private String projectId;
    private String syncStatus;
    private Instant lastSyncedAt;

    // Filter range applied
    private Instant filteredSince;
    private Instant filteredUntil;

    // Repository-level aggregates
    private int totalCommits;
    private int nonMergeCommits;
    private int mergeCommits;
    private int revertCommits;
    private int totalLinesAdded;
    private int totalLinesDeleted;
    private int netLinesTotal;
    private int totalFilesChanged;
    private int totalContributorsCount;
    private int mappedContributorsCount;
    private int unmatchedContributorsCount;

    // Per-member breakdowns
    private List<ContributorSummaryDto> contributors;

    // Timeline distribution
    private List<ContributionTimelinePointDto> timeline;

    // Methodological transparency explanation
    private CalculationMethodologyDto methodology;
}

