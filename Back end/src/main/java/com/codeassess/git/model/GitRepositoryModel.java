package com.codeassess.git.model;

import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitRepositoryModel {
    private String id;
    private String name;
    private String projectId;
    private String classId;
    private String repoUrl;
    @Builder.Default
    private String defaultBranch = "main";
    @Builder.Default
    private String provider = "GITHUB";
    private String accessTokenEncrypted;
    @Builder.Default
    private String syncStatus = "PENDING";
    private Instant lastSyncedAt;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Set<String> memberUserIds = new HashSet<>();
}

