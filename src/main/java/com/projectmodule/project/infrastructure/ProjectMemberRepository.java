package com.projectmodule.project.infrastructure;

import com.projectmodule.project.domain.ProjectMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for project membership. */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByProjectId(UUID projectId);

    List<ProjectMember> findByUserId(UUID userId);

    /** The membership that authorization reads to resolve a caller's role on a project. */
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
