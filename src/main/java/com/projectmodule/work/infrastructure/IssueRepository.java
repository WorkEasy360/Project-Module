package com.projectmodule.work.infrastructure;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.Issue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for issues. Archived issues are excluded explicitly, not by a global filter. */
public interface IssueRepository extends JpaRepository<Issue, UUID> {

    Optional<Issue> findByIdAndArchivedAtIsNull(UUID id);

    List<Issue> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    /**
     * For {@code docs/project/21-PROJECT-HEALTH-SPEC.md} §2: the "unresolved issue" indicator.
     * Issue has no status field, so "unresolved" is read as "still active" — the same
     * derived-count pattern {@code DecisionRepository.countByProjectIdAndArchivedAtIsNull}
     * already uses.
     */
    long countByProjectIdAndArchivedAtIsNull(UUID projectId);

    /**
     * Active issues in the given project whose {@code name} or {@code description} contains
     * {@code term}, case-insensitively. See {@code TaskRepository.search} for why this is
     * {@code @Query} rather than a derived method name. Unpaginated — see
     * {@code docs/project/13-SEARCH-SPEC.md} §4.
     */
    @Query("SELECT i FROM Issue i WHERE i.projectId = :projectId AND i.archivedAt IS NULL "
            + "AND (LOWER(i.name) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(i.description) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Issue> search(@Param("projectId") UUID projectId, @Param("term") String term);

    /**
     * Issues in the given project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}. See {@code TaskRepository.findByFilters} for why
     * this uses the {@code (:param IS NULL OR field = :param)} form and how {@code archived} is
     * a strict either/or, not a combined view. Takes a trailing {@link Sort} instead of a
     * hardcoded {@code ORDER BY} — see {@code docs/project/15-LIST-SPEC.md}.
     */
    @Query("SELECT i FROM Issue i WHERE i.projectId = :projectId "
            + "AND ((:archived = true AND i.archivedAt IS NOT NULL) OR (:archived = false AND i.archivedAt IS NULL)) "
            + "AND (:priority IS NULL OR i.priority = :priority)")
    List<Issue> findByFilters(@Param("projectId") UUID projectId,
                              @Param("priority") ProjectPriority priority,
                              @Param("archived") boolean archived,
                              Sort sort);

    /**
     * Active issue counts per priority, for {@code docs/project/24-REPORTS-SPEC.md} §4 — the
     * same {@code GROUP BY} exception {@code ProjectRepository.countActiveByStatus} already
     * establishes.
     */
    @Query("SELECT i.priority AS priority, COUNT(i) AS total FROM Issue i "
            + "WHERE i.projectId = :projectId AND i.archivedAt IS NULL GROUP BY i.priority")
    List<PriorityCount> countActiveByPriority(@Param("projectId") UUID projectId);

    /** Projection for {@link #countActiveByPriority}. */
    interface PriorityCount {
        ProjectPriority getPriority();

        long getTotal();
    }
}
