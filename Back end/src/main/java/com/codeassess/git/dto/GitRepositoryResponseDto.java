package com.codeassess.git.dto;

import lombok.*;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitRepositoryResponseDto {
    private String id;
    private String name;
    private String projectId;
    private String classId;
    private String repoUrl;
    private String defaultBranch;
    private String provider;
    private String syncStatus;
    private Instant lastSyncedAt;
    private Instant createdAt;
    private Set<String> memberUserIds;
    private int totalCommits;
    private int totalContributors;
}

