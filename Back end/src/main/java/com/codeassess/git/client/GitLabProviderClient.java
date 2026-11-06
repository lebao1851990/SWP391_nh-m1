package com.codeassess.git.client;

import com.codeassess.git.dto.GitCommitDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class GitLabProviderClient implements GitProviderClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern GITLAB_URL_PATTERN = Pattern.compile(
            "^(?:https?:\\/\\/gitlab\\.com\\/|git@gitlab\\.com:)([\\w.-]+(?:\\/[\\w.-]+)+)$"
    );

    @Override
    public String getProviderName() {
        return "GITLAB";
    }

    @Override
    public List<GitCommitDto> fetchCommits(String repoUrl, String branch, String accessToken, Instant since, Instant until, int maxPages) {
        String projectPath = extractProjectPath(repoUrl);
        if (projectPath == null) {
            log.error("Invalid GitLab repository URL: {}", repoUrl);
            throw new IllegalArgumentException("Invalid GitLab repository URL: " + repoUrl);
        }

        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        List<GitCommitDto> allCommits = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        int pagesToFetch = (maxPages > 0) ? maxPages : 5;

        while (page <= pagesToFetch) {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(
                    "https://gitlab.com/api/v4/projects/" + encodedPath + "/repository/commits"
            )
                    .queryParam("per_page", perPage)
                    .queryParam("page", page)
                    .queryParam("with_stats", "true");

            if (branch != null && !branch.trim().isEmpty()) {
                uriBuilder.queryParam("ref_name", branch.trim());
            }
            if (since != null) {
                uriBuilder.queryParam("since", since.toString());
            }
            if (until != null) {
                uriBuilder.queryParam("until", until.toString());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "CodeAssess-GitAnalytics/1.0");

            if (accessToken != null && !accessToken.trim().isEmpty()) {
                headers.set("PRIVATE-TOKEN", accessToken.trim());
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        uriBuilder.toUriString(), HttpMethod.GET, entity, String.class
                );

                // Check rate limits if present in GitLab headers
                String rateRemaining = response.getHeaders().getFirst("RateLimit-Remaining");
                if (rateRemaining != null && Integer.parseInt(rateRemaining) < 10) {
                    log.warn("GitLab API rate limit remaining is low: {}", rateRemaining);
                }

                if (response.getBody() == null || response.getBody().trim().isEmpty()) {
                    break;
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                if (!root.isArray() || root.isEmpty()) {
                    break;
                }

                for (JsonNode node : root) {
                    GitCommitDto dto = parseGitLabCommitNode(node);
                    if (dto != null) {
                        allCommits.add(dto);
                    }
                }

                if (root.size() < perPage) {
                    break;
                }
                page++;
            } catch (Exception ex) {
                log.error("Failed fetching commits from GitLab (page {}): {}", page, ex.getMessage());
                break;
            }
        }

        return allCommits;
    }

    @Override
    public boolean validateAccess(String repoUrl, String accessToken) {
        String projectPath = extractProjectPath(repoUrl);
        if (projectPath == null) return false;

        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        String url = "https://gitlab.com/api/v4/projects/" + encodedPath;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("User-Agent", "CodeAssess-GitAnalytics/1.0");
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            headers.set("PRIVATE-TOKEN", accessToken.trim());
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("GitLab validateAccess failed for {}: {}", repoUrl, ex.getMessage());
            return false;
        }
    }

    public String extractProjectPath(String repoUrl) {
        if (repoUrl == null) return null;
        String clean = repoUrl.trim();
        if (clean.endsWith(".git")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        Matcher matcher = GITLAB_URL_PATTERN.matcher(clean);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private GitCommitDto parseGitLabCommitNode(JsonNode node) {
        try {
            String hash = node.path("id").asText();
            String authorName = node.path("author_name").asText("Unknown");
            String authorEmail = node.path("author_email").asText("unknown@git");
            String message = node.path("message").asText("");
            String authoredDate = node.path("authored_date").asText();

            Instant date = Instant.now();
            if (authoredDate != null && !authoredDate.isEmpty()) {
                try {
                    date = Instant.parse(authoredDate);
                } catch (Exception ignored) {}
            }

            List<String> parents = new ArrayList<>();
            JsonNode parentIds = node.path("parent_ids");
            if (parentIds.isArray()) {
                for (JsonNode p : parentIds) {
                    parents.add(p.asText());
                }
            }

            JsonNode stats = node.path("stats");
            int additions = stats.path("additions").asInt(0);
            int deletions = stats.path("deletions").asInt(0);

            boolean isMerge = parents.size() > 1;

            return GitCommitDto.builder()
                    .commitHash(hash)
                    .authorName(authorName)
                    .authorEmail(authorEmail)
                    .commitDate(date)
                    .commitMessage(message)
                    .linesAdded(additions)
                    .linesDeleted(deletions)
                    .filesChanged(0) // GitLab commit stats omit file count without diff
                    .isMergeCommit(isMerge)
                    .parentHashes(parents)
                    .build();
        } catch (Exception ex) {
            log.warn("Failed to parse GitLab commit node: {}", ex.getMessage());
            return null;
        }
    }
}
