package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.ProjectComment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for project comments. Archived comments are excluded explicitly, not by a global filter. */
public interface ProjectCommentRepository extends JpaRepository<ProjectComment, UUID> {

    Optional<ProjectComment> findByIdAndArchivedAtIsNull(UUID id);

    /**
     * Ordering comes from {@code Pageable}, not a static {@code OrderBy} suffix — the same
     * convention {@code ProjectRepository.findByOrganizationIdAndArchivedAtIsNull} uses. The
     * controller supplies the default (oldest-first, per the approved specification).
     */
    Page<ProjectComment> findByProjectIdAndArchivedAtIsNull(UUID projectId, Pageable pageable);
}
