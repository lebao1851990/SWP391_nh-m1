package com.codeassess.git.model;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitCommitModel {
    private String id;
    private String repositoryId;
    private String commitHash;
    private String authorName;
    private String authorEmail;
    private String userId; // Nullable if contributor is unmatched
    private Instant commitDate;
    private String commitMessage;
    private int linesAdded;
    private int linesDeleted;
    private int filesChanged;
    @Builder.Default
    private boolean isMergeCommit = false;
    @Builder.Default
    private boolean isRevertCommit = false;
    @Builder.Default
    private List<String> parentHashes = new ArrayList<>();
    @Builder.Default
    private Instant createdAt = Instant.now();
}

