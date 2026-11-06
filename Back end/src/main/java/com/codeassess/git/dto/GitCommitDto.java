package com.codeassess.git.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitCommitDto {
    private String id;
    private String repositoryId;
    private String commitHash;
    private String authorName;
    private String authorEmail;
    private String userId;
    private String userFullName;
    private Instant commitDate;
    private String commitMessage;
    private int linesAdded;
    private int linesDeleted;
    private int filesChanged;
    private boolean isMergeCommit;
    private boolean isRevertCommit;
    private List<String> parentHashes;
}

