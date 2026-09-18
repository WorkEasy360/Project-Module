package com.projectmodule.work.infrastructure;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.RiskStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for risks. Archived risks are excluded explicitly, not by a global filter. */
public interface RiskRepository extends JpaRepository<Risk, UUID> {

    Optional<Risk> findByIdAndArchivedAtIsNull(UUID id);

    List<Risk> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    /**
     * Active risks in the given project whose {@code name} or {@code description} contains
     * {@code term}, case-insensitively. See {@code TaskRepository.search} for why this is
     * {@code @Query} rather than a derived method name. Unpaginated — see
     * {@code docs/project/13-SEARCH-SPEC.md} §4.
     */
    @Query("SELECT r FROM Risk r WHERE r.projectId = :projectId AND r.archivedAt IS NULL "
            + "AND (LOWER(r.name) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(r.description) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Risk> search(@Param("projectId") UUID projectId, @Param("term") String term);

    /**
     * Risks in the given project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}. See {@code TaskRepository.findByFilters} for why
     * this uses the {@code (:param IS NULL OR field = :param)} form and how {@code archived} is
     * a strict either/or, not a combined view. Takes a trailing {@link Sort} instead of a
     * hardcoded {@code ORDER BY} — see {@code docs/project/15-LIST-SPEC.md}.
     */
    @Query("SELECT r FROM Risk r WHERE r.projectId = :projectId "
            + "AND ((:archived = true AND r.archivedAt IS NOT NULL) OR (:archived = false AND r.archivedAt IS NULL)) "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND (:priority IS NULL OR r.priority = :priority)")
    List<Risk> findByFilters(@Param("projectId") UUID projectId,
                             @Param("status") RiskStatus status,
                             @Param("priority") ProjectPriority priority,
                             @Param("archived") boolean archived,
                             Sort sort);

    /**
     * Active risk counts per status/priority, for {@code docs/project/24-REPORTS-SPEC.md} §4 —
     * the same {@code GROUP BY} exception {@code ProjectRepository.countActiveByStatus} already
     * establishes.
     */
    @Query("SELECT r.status AS status, COUNT(r) AS total FROM Risk r "
            + "WHERE r.projectId = :projectId AND r.archivedAt IS NULL GROUP BY r.status")
    List<StatusCount> countActiveByStatus(@Param("projectId") UUID projectId);

    @Query("SELECT r.priority AS priority, COUNT(r) AS total FROM Risk r "
            + "WHERE r.projectId = :projectId AND r.archivedAt IS NULL GROUP BY r.priority")
    List<PriorityCount> countActiveByPriority(@Param("projectId") UUID projectId);

    /** Projection for {@link #countActiveByStatus}. */
    interface StatusCount {
        RiskStatus getStatus();

        long getTotal();
    }

    /** Projection for {@link #countActiveByPriority}. */
    interface PriorityCount {
        ProjectPriority getPriority();

        long getTotal();
    }
}
