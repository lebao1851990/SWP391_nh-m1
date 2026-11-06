package com.codeassess.git.client;

import com.codeassess.git.dto.GitCommitDto;

import java.time.Instant;
import java.util.List;

public interface GitProviderClient {
    String getProviderName();
    List<GitCommitDto> fetchCommits(String repoUrl, String branch, String accessToken, Instant since, Instant until, int maxPages);
    boolean validateAccess(String repoUrl, String accessToken);
}

