package com.codeassess.git.repository;

import com.codeassess.git.entity.GitRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GitRepositoryJpaRepository extends JpaRepository<GitRepositoryEntity, String> {
    List<GitRepositoryEntity> findByProjectId(String projectId);
    List<GitRepositoryEntity> findByClassId(String classId);
}

