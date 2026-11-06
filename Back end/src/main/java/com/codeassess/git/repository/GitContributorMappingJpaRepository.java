package com.codeassess.git.repository;

import com.codeassess.git.entity.GitContributorMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GitContributorMappingJpaRepository extends JpaRepository<GitContributorMappingEntity, String> {
    Optional<GitContributorMappingEntity> findByRepositoryIdAndGitAuthorEmailIgnoreCase(String repositoryId, String gitAuthorEmail);
    List<GitContributorMappingEntity> findByRepositoryId(String repositoryId);
}

