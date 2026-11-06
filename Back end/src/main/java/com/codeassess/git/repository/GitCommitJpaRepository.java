package com.codeassess.git.repository;

import com.codeassess.git.entity.GitCommitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GitCommitJpaRepository extends JpaRepository<GitCommitEntity, String> {
    Optional<GitCommitEntity> findByRepositoryIdAndCommitHash(String repositoryId, String commitHash);

    @Query("SELECT c FROM GitCommitEntity c WHERE c.repositoryId = :repoId " +
           "AND (:since IS NULL OR c.commitDate >= :since) " +
           "AND (:until IS NULL OR c.commitDate <= :until) " +
           "ORDER BY c.commitDate DESC")
    List<GitCommitEntity> findCommitsByDateRange(
            @Param("repoId") String repositoryId,
            @Param("since") Instant since,
            @Param("until") Instant until);

    int countByRepositoryId(String repositoryId);
}

