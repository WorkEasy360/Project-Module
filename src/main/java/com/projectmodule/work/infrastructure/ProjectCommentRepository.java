package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.ProjectComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for project comments. Archived comments are excluded explicitly, not by a global filter. */
public interface ProjectCommentRepository extends JpaRepository<ProjectComment, UUID> {

    Optional<ProjectComment> findByIdAndArchivedAtIsNull(UUID id);

    /**
     * Ordering comes from {@code Pageable}, not a static {@code OrderBy} suffix — the same
     * convention {@code ProjectRepository.findByOrganizationIdAndArchivedAtIsNull} uses. The
     * controller supplies the default (oldest-first, per the approved specification).
     */
    @Query("select c from ProjectComment c where c.projectId = :projectId and c.taskId is null and c.archivedAt is null")
    Page<ProjectComment> findProjectLevelComments(@Param("projectId") UUID projectId, Pageable pageable);

    List<ProjectComment> findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID taskId);
}
