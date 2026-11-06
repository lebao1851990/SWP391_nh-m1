package com.codeassess.git.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitSyncRequestDto {
    private String branch;
    private boolean forceResync;
    private Instant since;
    private Instant until;
    private Integer maxPages;
    private List<GitCommitDto> customCommits; // Allows direct ingestion / webhook payload
}
