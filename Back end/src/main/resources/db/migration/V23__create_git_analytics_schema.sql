-- Migration V23: Create Git Contribution Analytics Schema
-- Supports repository registration, contributor alias mappings, and commit history analytics.

CREATE TABLE git_repositories (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NULL,
    name VARCHAR(255) NOT NULL,
    repo_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    provider VARCHAR(50) NOT NULL DEFAULT 'GITHUB',
    access_token_encrypted VARCHAR(512) NULL,
    sync_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    last_synced_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    member_user_ids TEXT NULL,
    INDEX idx_git_repo_project (project_id),
    INDEX idx_git_repo_class (class_id)
);

CREATE TABLE git_contributor_mappings (
    id VARCHAR(36) PRIMARY KEY,
    repository_id VARCHAR(36) NOT NULL,
    git_author_name VARCHAR(255) NOT NULL,
    git_author_email VARCHAR(255) NOT NULL,
    user_id VARCHAR(36) NULL,
    mapped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_git_contrib_repo FOREIGN KEY (repository_id) REFERENCES git_repositories(id) ON DELETE CASCADE,
    CONSTRAINT fk_git_contrib_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_git_repo_author (repository_id, git_author_email)
);

CREATE TABLE git_commits (
    id VARCHAR(36) PRIMARY KEY,
    repository_id VARCHAR(36) NOT NULL,
    commit_hash VARCHAR(64) NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    author_email VARCHAR(255) NOT NULL,
    user_id VARCHAR(36) NULL,
    commit_date TIMESTAMP NOT NULL,
    commit_message TEXT NOT NULL,
    lines_added INT NOT NULL DEFAULT 0,
    lines_deleted INT NOT NULL DEFAULT 0,
    files_changed INT NOT NULL DEFAULT 0,
    is_merge_commit BOOLEAN NOT NULL DEFAULT FALSE,
    is_revert_commit BOOLEAN NOT NULL DEFAULT FALSE,
    parent_hashes VARCHAR(512) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_git_commit_repo FOREIGN KEY (repository_id) REFERENCES git_repositories(id) ON DELETE CASCADE,
    UNIQUE KEY uk_git_commit_hash (repository_id, commit_hash),
    INDEX idx_git_commit_date (repository_id, commit_date),
    INDEX idx_git_commit_user (repository_id, user_id)
);

