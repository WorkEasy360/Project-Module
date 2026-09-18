package com.projectmodule.project.infrastructure;

import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for the project aggregate.
 *
 * <p>Archived projects are excluded explicitly by the finders that should not see them rather
 * than by a global filter. A hidden filter makes it hard to reason about which query sees what,
 * and administrative and audit reads legitimately need the archived rows.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndArchivedAtIsNull(UUID id);

    Page<Project> findByOrganizationIdAndArchivedAtIsNull(UUID organizationId, Pageable pageable);

    List<Project> findByOwnerIdAndArchivedAtIsNull(UUID ownerId);

    long countByOrganizationIdAndArchivedAtIsNull(UUID organizationId);

    long countByOrganizationIdAndArchivedAtIsNotNull(UUID organizationId);

    boolean existsByIdAndArchivedAtIsNull(UUID id);

    /**
     * Active (non-archived) project counts per status, for the dashboard read model. A derived
     * method name cannot express {@code GROUP BY}, so this is the one query in the module
     * written as JPQL rather than a Spring Data method name.
     */
    @Query("SELECT p.status AS status, COUNT(p) AS total FROM Project p "
            + "WHERE p.organizationId = :organizationId AND p.archivedAt IS NULL "
            + "GROUP BY p.status")
    List<StatusCount> countActiveByStatus(@Param("organizationId") UUID organizationId);

    /** Active (non-archived) project counts per priority, for the dashboard read model. */
    @Query("SELECT p.priority AS priority, COUNT(p) AS total FROM Project p "
            + "WHERE p.organizationId = :organizationId AND p.archivedAt IS NULL "
            + "GROUP BY p.priority")
    List<PriorityCount> countActiveByPriority(@Param("organizationId") UUID organizationId);

    /** Projection for {@link #countActiveByStatus}. */
    interface StatusCount {
        ProjectStatus getStatus();

        long getTotal();
    }

    /** Projection for {@link #countActiveByPriority}. */
    interface PriorityCount {
        ProjectPriority getPriority();

        long getTotal();
    }
}
