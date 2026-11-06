package com.codeassess.git.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "git_commits", uniqueConstraints = {
        @UniqueConstraint(name = "uk_git_commit_hash", columnNames = {"repository_id", "commit_hash"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitCommitEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "repository_id", nullable = false, length = 36)
    private String repositoryId;

    @Column(name = "commit_hash", nullable = false, length = 64)
    private String commitHash;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_email", nullable = false)
    private String authorEmail;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "commit_date", nullable = false)
    private Instant commitDate;

    @Column(name = "commit_message", nullable = false, columnDefinition = "TEXT")
    private String commitMessage;

    @Column(name = "lines_added", nullable = false)
    @Builder.Default
    private int linesAdded = 0;

    @Column(name = "lines_deleted", nullable = false)
    @Builder.Default
    private int linesDeleted = 0;

    @Column(name = "files_changed", nullable = false)
    @Builder.Default
    private int filesChanged = 0;

    @Column(name = "is_merge_commit", nullable = false)
    @Builder.Default
    private boolean isMergeCommit = false;

    @Column(name = "is_revert_commit", nullable = false)
    @Builder.Default
    private boolean isRevertCommit = false;

    @Column(name = "parent_hashes", length = 512)
    private String parentHashes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}

