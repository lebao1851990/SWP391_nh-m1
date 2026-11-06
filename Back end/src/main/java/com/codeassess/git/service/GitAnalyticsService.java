package com.codeassess.git.service;

import com.codeassess.git.dto.*;
import com.codeassess.git.model.GitCommitModel;
import com.codeassess.git.model.GitContributorMappingModel;
import com.codeassess.git.model.GitRepositoryModel;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitAnalyticsService {

    private final GitStorageService storageService;
    private final UserRepository userRepository;

    public GitRepositoryResponseDto registerRepository(GitRepositoryRequestDto req) {
        GitRepositoryModel model = GitRepositoryModel.builder()
                .name(req.getName().trim())
                .projectId(req.getProjectId().trim())
                .classId(req.getClassId() != null ? req.getClassId().trim() : null)
                .repoUrl(req.getRepoUrl().trim())
                .defaultBranch(req.getDefaultBranch() != null ? req.getDefaultBranch().trim() : "main")
                .provider(req.getProvider() != null ? req.getProvider().toUpperCase().trim() : "GITHUB")
                .accessTokenEncrypted(req.getAccessToken() != null ? req.getAccessToken().trim() : null)
                .memberUserIds(req.getMemberUserIds() != null ? new HashSet<>(req.getMemberUserIds()) : new HashSet<>())
                .syncStatus("PENDING")
                .build();

        GitRepositoryModel saved = storageService.saveRepository(model);
        return mapToRepoResponse(saved);
    }

    public Optional<GitRepositoryResponseDto> getRepository(String id) {
        return storageService.findRepositoryById(id).map(this::mapToRepoResponse);
    }

    public List<GitRepositoryResponseDto> listRepositoriesForUser(String userId, boolean isTeacherOrAdmin) {
        if (isTeacherOrAdmin) {
            return storageService.findAllRepositories().stream()
                    .map(this::mapToRepoResponse)
                    .collect(Collectors.toList());
        }
        return storageService.findRepositoriesByMemberUserId(userId).stream()
                .map(this::mapToRepoResponse)
                .collect(Collectors.toList());
    }

    public RepositoryAnalyticsResponseDto calculateAnalytics(String repositoryId, Instant since, Instant until) {
        GitRepositoryModel repo = storageService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with ID: " + repositoryId));

        List<GitCommitModel> commits = storageService.findCommitsByRepository(repositoryId, since, until);

        // Preload user map for quick name resolution
        Map<String, String> userNames = loadUserNames();

        // 1. Repository-Level Aggregates
        int totalCommits = commits.size();
        int nonMergeCommits = 0;
        int mergeCommits = 0;
        int revertCommits = 0;
        int totalLinesAdded = 0;
        int totalLinesDeleted = 0;
        int totalFilesChanged = 0;

        // Group commits by author email (case-insensitive)
        Map<String, List<GitCommitModel>> byAuthor = new HashMap<>();
        Set<LocalDate> allProjectActiveDates = new HashSet<>();

        for (GitCommitModel c : commits) {
            String authorKey = c.getAuthorEmail().toLowerCase().trim();
            byAuthor.computeIfAbsent(authorKey, k -> new ArrayList<>()).add(c);

            LocalDate commitDay = c.getCommitDate().atZone(ZoneId.of("UTC")).toLocalDate();
            allProjectActiveDates.add(commitDay);

            if (c.isMergeCommit()) {
                mergeCommits++;
            } else {
                nonMergeCommits++;
                totalLinesAdded += c.getLinesAdded();
                totalLinesDeleted += c.getLinesDeleted();
                totalFilesChanged += c.getFilesChanged();
            }

            if (c.isRevertCommit()) {
                revertCommits++;
            }
        }

        int totalProjectDays = Math.max(1, allProjectActiveDates.size());

        // 2. Per-Contributor Summaries
        List<ContributorSummaryDto> contributorSummaries = new ArrayList<>();
        int mappedCount = 0;
        int unmatchedCount = 0;

        for (Map.Entry<String, List<GitCommitModel>> entry : byAuthor.entrySet()) {
            String email = entry.getKey();
            List<GitCommitModel> authorCommits = entry.getValue();

            String authorName = authorCommits.get(0).getAuthorName();
            String userId = authorCommits.stream().map(GitCommitModel::getUserId).filter(Objects::nonNull).findFirst().orElse(null);

            boolean isMapped = (userId != null);
            if (isMapped) mappedCount++; else unmatchedCount++;

            String fullName = (userId != null) ? userNames.getOrDefault(userId, authorName) : authorName;

            int cTotal = authorCommits.size();
            int cNonMerge = 0;
            int cMerge = 0;
            int cRevert = 0;
            int lAdded = 0;
            int lDeleted = 0;
            int fChanged = 0;

            Set<LocalDate> activeDays = new HashSet<>();
            Instant firstCommit = null;
            Instant lastCommit = null;

            for (GitCommitModel ac : authorCommits) {
                LocalDate day = ac.getCommitDate().atZone(ZoneId.of("UTC")).toLocalDate();
                activeDays.add(day);

                if (firstCommit == null || ac.getCommitDate().isBefore(firstCommit)) {
                    firstCommit = ac.getCommitDate();
                }
                if (lastCommit == null || ac.getCommitDate().isAfter(lastCommit)) {
                    lastCommit = ac.getCommitDate();
                }

                if (ac.isMergeCommit()) {
                    cMerge++;
                } else {
                    cNonMerge++;
                    lAdded += ac.getLinesAdded();
                    lDeleted += ac.getLinesDeleted();
                    fChanged += ac.getFilesChanged();
                }

                if (ac.isRevertCommit()) {
                    cRevert++;
                }
            }

            double commitPct = totalCommits > 0 ? (double) cTotal / totalCommits * 100.0 : 0.0;
            double lineAddPct = totalLinesAdded > 0 ? (double) lAdded / totalLinesAdded * 100.0 : 0.0;
            double consistencyRatio = (double) activeDays.size() / totalProjectDays;
            double churnBalance = (double) lDeleted / Math.max(1, lAdded);

            contributorSummaries.add(ContributorSummaryDto.builder()
                    .gitAuthorEmail(email)
                    .gitAuthorName(authorName)
                    .userId(userId)
                    .userFullName(fullName)
                    .isMapped(isMapped)
                    .totalCommits(cTotal)
                    .nonMergeCommits(cNonMerge)
                    .mergeCommits(cMerge)
                    .revertCommits(cRevert)
                    .linesAdded(lAdded)
                    .linesDeleted(lDeleted)
                    .netLines(lAdded - lDeleted)
                    .filesChanged(fChanged)
                    .activeDays(activeDays.size())
                    .firstCommitAt(firstCommit)
                    .lastCommitAt(lastCommit)
                    .commitPercentage(Math.round(commitPct * 10.0) / 10.0)
                    .lineAdditionPercentage(Math.round(lineAddPct * 10.0) / 10.0)
                    .consistencyRatio(Math.round(consistencyRatio * 100.0) / 100.0)
                    .churnBalanceRatio(Math.round(churnBalance * 100.0) / 100.0)
                    .build());
        }

        // Sort contributors by commit count descending
        contributorSummaries.sort(Comparator.comparingInt(ContributorSummaryDto::getTotalCommits).reversed());

        // 3. Timeline Breakdown (by calendar day)
        List<ContributionTimelinePointDto> timeline = buildTimeline(commits);

        // 4. Transparent Methodology Specification
        CalculationMethodologyDto methodology = getMethodologyExplanation();

        return RepositoryAnalyticsResponseDto.builder()
                .repositoryId(repo.getId())
                .repositoryName(repo.getName())
                .repoUrl(repo.getRepoUrl())
                .defaultBranch(repo.getDefaultBranch())
                .projectId(repo.getProjectId())
                .syncStatus(repo.getSyncStatus())
                .lastSyncedAt(repo.getLastSyncedAt())
                .filteredSince(since)
                .filteredUntil(until)
                .totalCommits(totalCommits)
                .nonMergeCommits(nonMergeCommits)
                .mergeCommits(mergeCommits)
                .revertCommits(revertCommits)
                .totalLinesAdded(totalLinesAdded)
                .totalLinesDeleted(totalLinesDeleted)
                .netLinesTotal(totalLinesAdded - totalLinesDeleted)
                .totalFilesChanged(totalFilesChanged)
                .totalContributorsCount(byAuthor.size())
                .mappedContributorsCount(mappedCount)
                .unmatchedContributorsCount(unmatchedCount)
                .contributors(contributorSummaries)
                .timeline(timeline)
                .methodology(methodology)
                .build();
    }

    private List<ContributionTimelinePointDto> buildTimeline(List<GitCommitModel> commits) {
        Map<String, List<GitCommitModel>> byDate = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (GitCommitModel c : commits) {
            String dateStr = c.getCommitDate().atZone(ZoneId.of("UTC")).format(fmt);
            byDate.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(c);
        }

        List<ContributionTimelinePointDto> points = new ArrayList<>();
        for (Map.Entry<String, List<GitCommitModel>> entry : byDate.entrySet()) {
            String date = entry.getKey();
            List<GitCommitModel> dayCommits = entry.getValue();

            int tCommits = dayCommits.size();
            int tAdd = 0;
            int tDel = 0;
            Map<String, Integer> byContrib = new HashMap<>();

            for (GitCommitModel dc : dayCommits) {
                if (!dc.isMergeCommit()) {
                    tAdd += dc.getLinesAdded();
                    tDel += dc.getLinesDeleted();
                }
                byContrib.merge(dc.getAuthorEmail().toLowerCase().trim(), 1, Integer::sum);
            }

            points.add(ContributionTimelinePointDto.builder()
                    .date(date)
                    .totalCommits(tCommits)
                    .linesAdded(tAdd)
                    .linesDeleted(tDel)
                    .netLines(tAdd - tDel)
                    .commitsByContributor(byContrib)
                    .build());
        }

        return points;
    }

    public GitContributorMappingModel mapContributor(String repositoryId, ContributorMappingRequestDto req) {
        GitContributorMappingModel mapping = GitContributorMappingModel.builder()
                .repositoryId(repositoryId)
                .gitAuthorName(req.getGitAuthorName() != null ? req.getGitAuthorName().trim() : "")
                .gitAuthorEmail(req.getGitAuthorEmail().toLowerCase().trim())
                .userId(req.getUserId().trim())
                .mappedAt(Instant.now())
                .build();

        GitContributorMappingModel saved = storageService.saveMapping(mapping);

        // Update existing commits for this author
        List<GitCommitModel> existingCommits = storageService.findCommitsByRepository(repositoryId, null, null);
        for (GitCommitModel c : existingCommits) {
            if (c.getAuthorEmail().equalsIgnoreCase(mapping.getGitAuthorEmail())) {
                c.setUserId(mapping.getUserId());
                storageService.saveCommit(c);
            }
        }

        return saved;
    }

    public List<GitContributorMappingModel> getMappings(String repositoryId) {
        return storageService.findMappingsByRepository(repositoryId);
    }

    public CalculationMethodologyDto getMethodologyExplanation() {
        return CalculationMethodologyDto.builder()
                .ethicalNotice("Git contribution metrics are factual indicators of repository activity and MUST NOT be equated directly with student effort, project contribution, or academic performance.")
                .mergeCommitRule("Commits with more than one parent hash are identified as merge commits. Their line additions and deletions are excluded from contributor code counts to prevent branch double-counting.")
                .revertCommitRule("Commits reverting prior changes are tracked separately to provide visibility into code lifecycle and rework.")
                .lineCountRule("Lines added and deleted are calculated across non-merge commits. Net lines = additions - deletions.")
                .identityResolutionRule("Git authors are mapped to CodeAssess student accounts via manual alias mapping, exact email match, or university username prefix match.")
                .limitations(List.of(
                        "Code line volume does not reflect intellectual difficulty or algorithmic complexity.",
                        "Automated code formatters, linters, or imported template libraries can distort line counts.",
                        "Commit frequency varies widely by personal workflow (frequent micro-commits vs consolidated features).",
                        "Pair programming or collaborative debugging may be recorded under a single commit author."
                ))
                .recommendedInstructorGuidelines(List.of(
                        "Review commit message clarity, issue tracking, and peer reviews alongside numerical counts.",
                        "Inspect timeline consistency to distinguish steady collaborative progress from last-minute spikes.",
                        "Combine Git analytics with code quality reviews, rubric scoring, and oral defenses."
                ))
                .build();
    }

    private GitRepositoryResponseDto mapToRepoResponse(GitRepositoryModel r) {
        int commits = storageService.getCommitCount(r.getId());
        List<GitCommitModel> allCommits = storageService.findCommitsByRepository(r.getId(), null, null);
        long contributors = allCommits.stream().map(c -> c.getAuthorEmail().toLowerCase().trim()).distinct().count();

        return GitRepositoryResponseDto.builder()
                .id(r.getId())
                .name(r.getName())
                .projectId(r.getProjectId())
                .classId(r.getClassId())
                .repoUrl(r.getRepoUrl())
                .defaultBranch(r.getDefaultBranch())
                .provider(r.getProvider())
                .syncStatus(r.getSyncStatus())
                .lastSyncedAt(r.getLastSyncedAt())
                .createdAt(r.getCreatedAt())
                .memberUserIds(r.getMemberUserIds())
                .totalCommits(commits)
                .totalContributors((int) contributors)
                .build();
    }

    private Map<String, String> loadUserNames() {
        Map<String, String> names = new HashMap<>();
        try {
            List<User> users = userRepository.findAll();
            for (User u : users) {
                if (u.getId() != null) {
                    names.put(u.getId(), u.getFullName() != null ? u.getFullName() : u.getEmail());
                }
            }
        } catch (Exception e) {
            log.warn("Could not preload user names: {}", e.getMessage());
        }
        return names;
    }
}

