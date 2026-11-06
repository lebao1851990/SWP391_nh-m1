package com.codeassess.git.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "git_contributor_mappings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_git_repo_author", columnNames = {"repository_id", "git_author_email"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitContributorMappingEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "repository_id", nullable = false, length = 36)
    private String repositoryId;

    @Column(name = "git_author_name", nullable = false)
    private String gitAuthorName;

    @Column(name = "git_author_email", nullable = false)
    private String gitAuthorEmail;

    @Column(name = "user_id", length = 36)
    private String userId;

    @CreationTimestamp
    @Column(name = "mapped_at", updatable = false)
    private Instant mappedAt;
}

