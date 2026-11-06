package com.codeassess.git.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "git_repositories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitRepositoryEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "class_id", length = 36)
    private String classId;

    @Column(nullable = false)
    private String name;

    @Column(name = "repo_url", nullable = false, length = 512)
    private String repoUrl;

    @Column(name = "default_branch", nullable = false, length = 100)
    @Builder.Default
    private String defaultBranch = "main";

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String provider = "GITHUB";

    @Column(name = "access_token_encrypted", length = 512)
    private String accessTokenEncrypted;

    @Column(name = "sync_status", nullable = false, length = 50)
    @Builder.Default
    private String syncStatus = "PENDING";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "member_user_ids", columnDefinition = "TEXT")
    private String memberUserIdsJson;
}

