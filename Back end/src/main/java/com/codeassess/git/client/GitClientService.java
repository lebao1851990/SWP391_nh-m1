package com.codeassess.git.client;

import com.codeassess.git.dto.GitCommitDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class GitClientService {

    private final Map<String, GitProviderClient> providerClients = new ConcurrentHashMap<>();

    public GitClientService(List<GitProviderClient> clients) {
        for (GitProviderClient client : clients) {
            providerClients.put(client.getProviderName().toUpperCase(), client);
        }
    }

    public List<GitCommitDto> fetchCommits(String provider, String repoUrl, String branch, String accessToken, Instant since, Instant until, int maxPages) {
        GitProviderClient client = resolveClient(provider, repoUrl);
        return client.fetchCommits(repoUrl, branch, accessToken, since, until, maxPages);
    }

    public boolean validateAccess(String provider, String repoUrl, String accessToken) {
        GitProviderClient client = resolveClient(provider, repoUrl);
        return client.validateAccess(repoUrl, accessToken);
    }

    public GitProviderClient resolveClient(String provider, String repoUrl) {
        if (provider != null && !provider.trim().isEmpty()) {
            GitProviderClient client = providerClients.get(provider.trim().toUpperCase());
            if (client != null) {
                return client;
            }
        }

        // Auto-detect by URL if provider is null or unrecognized
        if (repoUrl != null) {
            String lower = repoUrl.toLowerCase();
            if (lower.contains("gitlab.com")) {
                GitProviderClient gitlab = providerClients.get("GITLAB");
                if (gitlab != null) return gitlab;
            } else if (lower.contains("github.com")) {
                GitProviderClient github = providerClients.get("GITHUB");
                if (github != null) return github;
            } else if (lower.contains("mock")) {
                GitProviderClient mock = providerClients.get("MOCK");
                if (mock != null) return mock;
            }
        }

        // Fallback default: MOCK or GITHUB
        return providerClients.getOrDefault("GITHUB", providerClients.get("MOCK"));
    }
}

