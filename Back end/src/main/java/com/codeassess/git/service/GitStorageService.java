package com.codeassess.git.service;

import com.codeassess.git.entity.GitCommitEntity;
import com.codeassess.git.entity.GitContributorMappingEntity;
import com.codeassess.git.entity.GitRepositoryEntity;
import com.codeassess.git.model.GitCommitModel;
import com.codeassess.git.model.GitContributorMappingModel;
import com.codeassess.git.model.GitRepositoryModel;
import com.codeassess.git.repository.GitCommitJpaRepository;
import com.codeassess.git.repository.GitContributorMappingJpaRepository;
import com.codeassess.git.repository.GitRepositoryJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Database-backed persistence service for Git Contribution Analytics.
 * Persists entities to MySQL across application restarts with idempotent uniqueness constraints.
 * Supports in-memory operation as fallback when JPA repositories are not injected.
 */
@Service
@Slf4j
public class GitStorageService {

    private final GitRepositoryJpaRepository repoJpa;
    private final GitCommitJpaRepository commitJpa;
    private final GitContributorMappingJpaRepository mappingJpa;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory fallback caches for unit test environments
    private final Map<String, GitRepositoryModel> memRepos = new ConcurrentHashMap<>();
    private final Map<String, List<GitCommitModel>> memCommits = new ConcurrentHashMap<>();
    private final Map<String, List<GitContributorMappingModel>> memMappings = new ConcurrentHashMap<>();

    @Autowired
    public GitStorageService(GitRepositoryJpaRepository repoJpa,
                             GitCommitJpaRepository commitJpa,
                             GitContributorMappingJpaRepository mappingJpa) {
        this.repoJpa = repoJpa;
        this.commitJpa = commitJpa;
        this.mappingJpa = mappingJpa;
    }

    // Default constructor for standalone unit tests
    public GitStorageService() {
        this.repoJpa = null;
        this.commitJpa = null;
        this.mappingJpa = null;
    }

    public void clearAll() {
        memRepos.clear();
        memCommits.clear();
        memMappings.clear();
        if (mappingJpa != null) {
            try {
                mappingJpa.deleteAll();
                commitJpa.deleteAll();
                repoJpa.deleteAll();
            } catch (Exception e) {
                log.warn("Error during clearAll: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public GitRepositoryModel saveRepository(GitRepositoryModel model) {
        if (model.getId() == null || model.getId().trim().isEmpty()) {
            model.setId(UUID.randomUUID().toString());
        }
        if (model.getCreatedAt() == null) {
            model.setCreatedAt(Instant.now());
        }

        if (repoJpa == null) {
            memRepos.put(model.getId(), model);
            return model;
        }

        String membersJson = null;
        if (model.getMemberUserIds() != null) {
            try {
                membersJson = objectMapper.writeValueAsString(model.getMemberUserIds());
            } catch (Exception e) {
                log.warn("Failed to serialize member user IDs: {}", e.getMessage());
            }
        }

        GitRepositoryEntity entity = GitRepositoryEntity.builder()
                .id(model.getId())
                .name(model.getName())
                .projectId(model.getProjectId())
                .classId(model.getClassId())
                .repoUrl(model.getRepoUrl())
                .defaultBranch(model.getDefaultBranch())
                .provider(model.getProvider())
                .accessTokenEncrypted(model.getAccessTokenEncrypted())
                .syncStatus(model.getSyncStatus())
                .lastSyncedAt(model.getLastSyncedAt())
                .createdAt(model.getCreatedAt())
                .memberUserIdsJson(membersJson)
                .build();

        GitRepositoryEntity saved = repoJpa.save(entity);
        return toModel(saved);
    }

    public Optional<GitRepositoryModel> findRepositoryById(String id) {
        if (repoJpa == null) {
            return Optional.ofNullable(memRepos.get(id));
        }
        return repoJpa.findById(id).map(this::toModel);
    }

    public List<GitRepositoryModel> findAllRepositories() {
        if (repoJpa == null) {
            return new ArrayList<>(memRepos.values());
        }
        return repoJpa.findAll().stream().map(this::toModel).collect(Collectors.toList());
    }

    public List<GitRepositoryModel> findRepositoriesByProjectId(String projectId) {
        if (repoJpa == null) {
            return memRepos.values().stream()
                    .filter(r -> projectId != null && projectId.equals(r.getProjectId()))
                    .collect(Collectors.toList());
        }
        return repoJpa.findByProjectId(projectId).stream().map(this::toModel).collect(Collectors.toList());
    }

    public List<GitRepositoryModel> findRepositoriesByMemberUserId(String userId) {
        if (repoJpa == null) {
            return memRepos.values().stream()
                    .filter(r -> r.getMemberUserIds() != null && r.getMemberUserIds().contains(userId))
                    .collect(Collectors.toList());
        }
        return repoJpa.findAll().stream()
                .map(this::toModel)
                .filter(r -> r.getMemberUserIds() != null && r.getMemberUserIds().contains(userId))
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean deleteRepository(String id) {
        if (repoJpa == null) {
            return memRepos.remove(id) != null;
        }
        if (repoJpa.existsById(id)) {
            repoJpa.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Idempotent commit persistence against MySQL unique constraint (repository_id, commit_hash).
     * If commit exists, updates stats in-place; otherwise inserts new record.
     */
    @Transactional
    public GitCommitModel saveCommit(GitCommitModel model) {
        if (commitJpa == null) {
            List<GitCommitModel> list = memCommits.computeIfAbsent(model.getRepositoryId(), k -> new ArrayList<>());
            list.removeIf(c -> c.getCommitHash().equalsIgnoreCase(model.getCommitHash()));
            if (model.getId() == null) model.setId(UUID.randomUUID().toString());
            list.add(model);
            return model;
        }

        Optional<GitCommitEntity> existing = commitJpa.findByRepositoryIdAndCommitHash(
                model.getRepositoryId(), model.getCommitHash()
        );

        String id = existing.map(GitCommitEntity::getId).orElse(
                model.getId() != null && !model.getId().trim().isEmpty() ? model.getId() : UUID.randomUUID().toString()
        );
        Instant created = existing.map(GitCommitEntity::getCreatedAt).orElse(
                model.getCreatedAt() != null ? model.getCreatedAt() : Instant.now()
        );

        String parentHashesStr = (model.getParentHashes() != null)
                ? String.join(",", model.getParentHashes())
                : null;

        GitCommitEntity entity = GitCommitEntity.builder()
                .id(id)
                .repositoryId(model.getRepositoryId())
                .commitHash(model.getCommitHash())
                .authorName(model.getAuthorName())
                .authorEmail(model.getAuthorEmail())
                .userId(model.getUserId())
                .commitDate(model.getCommitDate())
                .commitMessage(model.getCommitMessage())
                .linesAdded(model.getLinesAdded())
                .linesDeleted(model.getLinesDeleted())
                .filesChanged(model.getFilesChanged())
                .isMergeCommit(model.isMergeCommit())
                .isRevertCommit(model.isRevertCommit())
                .parentHashes(parentHashesStr)
                .createdAt(created)
                .build();

        GitCommitEntity saved = commitJpa.save(entity);
        return toModel(saved);
    }

    public List<GitCommitModel> findCommitsByRepository(String repositoryId, Instant since, Instant until) {
        if (commitJpa == null) {
            List<GitCommitModel> list = memCommits.getOrDefault(repositoryId, Collections.emptyList());
            return list.stream()
                    .filter(c -> since == null || !c.getCommitDate().isBefore(since))
                    .filter(c -> until == null || !c.getCommitDate().isAfter(until))
                    .collect(Collectors.toList());
        }
        return commitJpa.findCommitsByDateRange(repositoryId, since, until).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public int getCommitCount(String repositoryId) {
        if (commitJpa == null) {
            return memCommits.getOrDefault(repositoryId, Collections.emptyList()).size();
        }
        return commitJpa.countByRepositoryId(repositoryId);
    }

    @Transactional
    public GitContributorMappingModel saveMapping(GitContributorMappingModel model) {
        String cleanEmail = model.getGitAuthorEmail().toLowerCase().trim();

        if (mappingJpa == null) {
            List<GitContributorMappingModel> list = memMappings.computeIfAbsent(model.getRepositoryId(), k -> new ArrayList<>());
            list.removeIf(m -> m.getGitAuthorEmail().equalsIgnoreCase(cleanEmail));
            if (model.getId() == null) model.setId(UUID.randomUUID().toString());
            list.add(model);
            return model;
        }

        Optional<GitContributorMappingEntity> existing = mappingJpa.findByRepositoryIdAndGitAuthorEmailIgnoreCase(
                model.getRepositoryId(), cleanEmail
        );

        String id = existing.map(GitContributorMappingEntity::getId).orElse(
                model.getId() != null && !model.getId().trim().isEmpty() ? model.getId() : UUID.randomUUID().toString()
        );

        GitContributorMappingEntity entity = GitContributorMappingEntity.builder()
                .id(id)
                .repositoryId(model.getRepositoryId())
                .gitAuthorName(model.getGitAuthorName())
                .gitAuthorEmail(cleanEmail)
                .userId(model.getUserId())
                .mappedAt(model.getMappedAt() != null ? model.getMappedAt() : Instant.now())
                .build();

        GitContributorMappingEntity saved = mappingJpa.save(entity);
        return toModel(saved);
    }

    public Optional<GitContributorMappingModel> findMapping(String repositoryId, String gitAuthorEmail) {
        if (gitAuthorEmail == null) return Optional.empty();
        String cleanEmail = gitAuthorEmail.toLowerCase().trim();

        if (mappingJpa == null) {
            List<GitContributorMappingModel> list = memMappings.getOrDefault(repositoryId, Collections.emptyList());
            return list.stream()
                    .filter(m -> m.getGitAuthorEmail().equalsIgnoreCase(cleanEmail))
                    .findFirst();
        }

        return mappingJpa.findByRepositoryIdAndGitAuthorEmailIgnoreCase(repositoryId, cleanEmail)
                .map(this::toModel);
    }

    public List<GitContributorMappingModel> findMappingsByRepository(String repositoryId) {
        if (mappingJpa == null) {
            return new ArrayList<>(memMappings.getOrDefault(repositoryId, Collections.emptyList()));
        }
        return mappingJpa.findByRepositoryId(repositoryId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    // --- Entity/Model mappers ---

    private GitRepositoryModel toModel(GitRepositoryEntity e) {
        Set<String> members = new HashSet<>();
        if (e.getMemberUserIdsJson() != null && !e.getMemberUserIdsJson().trim().isEmpty()) {
            try {
                members = objectMapper.readValue(e.getMemberUserIdsJson(), new TypeReference<Set<String>>() {});
            } catch (Exception ex) {
                log.warn("Failed to deserialize member user IDs for repo {}: {}", e.getId(), ex.getMessage());
            }
        }

        return GitRepositoryModel.builder()
                .id(e.getId())
                .name(e.getName())
                .projectId(e.getProjectId())
                .classId(e.getClassId())
                .repoUrl(e.getRepoUrl())
                .defaultBranch(e.getDefaultBranch())
                .provider(e.getProvider())
                .accessTokenEncrypted(e.getAccessTokenEncrypted())
                .syncStatus(e.getSyncStatus())
                .lastSyncedAt(e.getLastSyncedAt())
                .createdAt(e.getCreatedAt())
                .memberUserIds(members)
                .build();
    }

    private GitCommitModel toModel(GitCommitEntity e) {
        List<String> parents = (e.getParentHashes() != null && !e.getParentHashes().trim().isEmpty())
                ? Arrays.asList(e.getParentHashes().split(","))
                : new ArrayList<>();

        return GitCommitModel.builder()
                .id(e.getId())
                .repositoryId(e.getRepositoryId())
                .commitHash(e.getCommitHash())
                .authorName(e.getAuthorName())
                .authorEmail(e.getAuthorEmail())
                .userId(e.getUserId())
                .commitDate(e.getCommitDate())
                .commitMessage(e.getCommitMessage())
                .linesAdded(e.getLinesAdded())
                .linesDeleted(e.getLinesDeleted())
                .filesChanged(e.getFilesChanged())
                .isMergeCommit(e.isMergeCommit())
                .isRevertCommit(e.isRevertCommit())
                .parentHashes(parents)
                .createdAt(e.getCreatedAt())
                .build();
    }

    private GitContributorMappingModel toModel(GitContributorMappingEntity e) {
        return GitContributorMappingModel.builder()
                .id(e.getId())
                .repositoryId(e.getRepositoryId())
                .gitAuthorName(e.getGitAuthorName())
                .gitAuthorEmail(e.getGitAuthorEmail())
                .userId(e.getUserId())
                .mappedAt(e.getMappedAt())
                .build();
    }
}
