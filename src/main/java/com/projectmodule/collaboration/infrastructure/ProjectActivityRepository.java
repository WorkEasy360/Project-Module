package com.projectmodule.collaboration.infrastructure;

import com.projectmodule.collaboration.domain.ProjectActivity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the audit trail.
 *
 * <p>Read and append only. No update or delete operation is exposed here, and none should be
 * added.
 */
public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, UUID> {

    Page<ProjectActivity> findByProjectIdOrderByOccurredAtDesc(UUID projectId, Pageable pageable);

    long countByProjectId(UUID projectId);
}
