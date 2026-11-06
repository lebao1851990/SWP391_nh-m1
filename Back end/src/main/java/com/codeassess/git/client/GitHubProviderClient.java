package com.codeassess.git.client;

import com.codeassess.git.dto.GitCommitDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class GitHubProviderClient implements GitProviderClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "^(?:https?:\\/\\/github\\.com\\/|git@github\\.com:)([\\w.-]+)\\/([\\w.-]+?)(?:\\.git)?$"
    );

    @Override
    public String getProviderName() {
        return "GITHUB";
    }

    @Override
    public List<GitCommitDto> fetchCommits(String repoUrl, String branch, String accessToken, Instant since, Instant until, int maxPages) {
        String repoSlug = extractRepoSlug(repoUrl);
        if (repoSlug == null) {
            log.error("Invalid GitHub repository URL: {}", repoUrl);
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + repoUrl);
        }

        List<GitCommitDto> allCommits = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        int pagesToFetch = (maxPages > 0) ? maxPages : 5; // safety cap on pagination

        while (page <= pagesToFetch) {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl("https://api.github.com/repos/" + repoSlug + "/commits")
                    .queryParam("per_page", perPage)
                    .queryParam("page", page);

            if (branch != null && !branch.trim().isEmpty()) {
                uriBuilder.queryParam("sha", branch.trim());
            }
            if (since != null) {
                uriBuilder.queryParam("since", since.toString());
            }
            if (until != null) {
                uriBuilder.queryParam("until", until.toString());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "CodeAssess-GitAnalytics/1.0");

            if (accessToken != null && !accessToken.trim().isEmpty()) {
                headers.setBearerAuth(accessToken.trim());
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        uriBuilder.toUriString(), HttpMethod.GET, entity, String.class
                );

                // Rate limit header logging (sanitized, zero secrets)
                String rateRemaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
                if (rateRemaining != null && Integer.parseInt(rateRemaining) < 10) {
                    log.warn("GitHub API rate limit remaining is low: {}/{}", rateRemaining, response.getHeaders().getFirst("X-RateLimit-Limit"));
                }

                if (response.getBody() == null || response.getBody().trim().isEmpty()) {
                    break;
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                if (!root.isArray() || root.isEmpty()) {
                    break;
                }

                for (JsonNode node : root) {
                    GitCommitDto dto = parseGitHubCommitNode(node);
                    if (dto != null) {
                        allCommits.add(dto);
                    }
                }

                if (root.size() < perPage) {
                    // Last page reached
                    break;
                }

                page++;
            } catch (Exception ex) {
                log.warn("GitHub API fetch error on page {} for repo {}: {}", page, repoSlug, ex.getMessage());
                break;
            }
        }

        return allCommits;
    }

    @Override
    public boolean validateAccess(String repoUrl, String accessToken) {
        String repoSlug = extractRepoSlug(repoUrl);
        if (repoSlug == null) return false;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "CodeAssess-GitAnalytics/1.0");
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            headers.setBearerAuth(accessToken.trim());
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + repoSlug,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private GitCommitDto parseGitHubCommitNode(JsonNode node) {
        try {
            String sha = node.path("sha").asText();
            JsonNode commitObj = node.path("commit");
            JsonNode authorObj = commitObj.path("author");

            String authorName = authorObj.path("name").asText("Unknown");
            String authorEmail = authorObj.path("email").asText("unknown@github.com");
            String message = commitObj.path("message").asText("");
            String dateStr = authorObj.path("date").asText();
            Instant commitDate = (dateStr != null && !dateStr.isEmpty()) ? Instant.parse(dateStr) : Instant.now();

            List<String> parents = new ArrayList<>();
            JsonNode parentsArray = node.path("parents");
            if (parentsArray.isArray()) {
                for (JsonNode p : parentsArray) {
                    parents.add(p.path("sha").asText());
                }
            }

            boolean isMerge = parents.size() > 1;

            // Stats (additions/deletions) if present in payload
            int additions = node.path("stats").path("additions").asInt(0);
            int deletions = node.path("stats").path("deletions").asInt(0);
            int filesChanged = node.path("files").isArray() ? node.path("files").size() : 0;

            return GitCommitDto.builder()
                    .commitHash(sha)
                    .authorName(authorName)
                    .authorEmail(authorEmail)
                    .commitDate(commitDate)
                    .commitMessage(message)
                    .linesAdded(additions)
                    .linesDeleted(deletions)
                    .filesChanged(filesChanged)
                    .parentHashes(parents)
                    .isMergeCommit(isMerge)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse GitHub commit JSON node: {}", e.getMessage());
            return null;
        }
    }

    public static String extractRepoSlug(String url) {
        if (url == null) return null;
        Matcher m = GITHUB_URL_PATTERN.matcher(url.trim());
        if (m.matches()) {
            return m.group(1) + "/" + m.group(2);
        }
        return null;
    }
}

