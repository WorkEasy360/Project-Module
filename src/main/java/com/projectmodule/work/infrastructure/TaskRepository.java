package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for tasks. Archived tasks are excluded explicitly, not by a global filter. */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndArchivedAtIsNull(UUID id);

    List<Task> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    /** For {@code docs/project/17-CALENDAR-SPEC.md} §5: active tasks with a real due date. */
    List<Task> findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(UUID projectId);

    /**
     * For {@code docs/project/22-DELAY-DETECTION-SPEC.md} §5: active, not-yet-completed tasks
     * whose due date has already passed.
     */
    List<Task> findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
            UUID projectId, LocalDate today, TaskStatus excludedStatus);

    /**
     * Active tasks in the given project whose {@code name} or {@code description} contains
     * {@code term}, case-insensitively. Written as {@code @Query} rather than a derived method
     * name — a shared-AND/OR-across-two-fields condition would otherwise require repeating
     * {@code projectId} and {@code term} as separate parameters on each side of the {@code Or},
     * the same reasoning {@code ProjectRepository.countActiveByStatus} already established for
     * this codebase's one other {@code @Query} usage. Unpaginated: per
     * {@code docs/project/13-SEARCH-SPEC.md} §4, merging and pagination happen in the
     * application layer across all four searched entity types, not per repository.
     */
    @Query("SELECT t FROM Task t WHERE t.projectId = :projectId AND t.archivedAt IS NULL "
            + "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Task> search(@Param("projectId") UUID projectId, @Param("term") String term);

    /**
     * Tasks in the given project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}. A {@code null} filter parameter is not applied —
     * the {@code (:param IS NULL OR field = :param)} form is the standard way to express
     * optional, independently-combinable equality filters in one JPQL query without a
     * {@code Specification}/{@code Criteria} builder, which this codebase has never used.
     * {@code archived}: {@code false} (the default) returns active rows only, exactly as
     * {@link #findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc} already does; {@code true}
     * returns archived rows only (§8) — there is no combined "both" mode in V1.
     * {@code dueDateBefore}/{@code dueDateAfter} are strict (exclusive) comparisons, the literal
     * reading of "before"/"after".
     *
     * <p>The {@code dueDateBefore}/{@code dueDateAfter} null checks are written as
     * {@code CAST(:param AS date) IS NULL} rather than the bare {@code :param IS NULL} used for
     * the other filters. Against real PostgreSQL, a bare-parameter {@code IS NULL} check on a
     * {@code LocalDate} placeholder that has no other typed context in the query fails with
     * {@code could not determine data type of parameter} once Hibernate binds a non-null value —
     * the {@code CAST} gives PostgreSQL an explicit type for that placeholder. {@code status} and
     * {@code assigneeId} do not need this: their enum/UUID bindings already carry an explicit
     * JDBC type from Hibernate, confirmed by the equivalent non-null-value tests for Risk/Issue
     * priority and Decision's {@code decidedBy} (also a UUID) passing without it.
     *
     * <p>Takes a trailing {@link Sort} rather than a hardcoded {@code ORDER BY}, per
     * {@code docs/project/15-LIST-SPEC.md} — Spring Data JPA applies a trailing {@code Sort}
     * argument to a {@code @Query} method automatically. Callers with no sort preference pass
     * {@code Sort.by("createdAt").ascending()}, reproducing this method's original fixed order
     * exactly.
     */
    @Query("SELECT t FROM Task t WHERE t.projectId = :projectId "
            + "AND ((:archived = true AND t.archivedAt IS NOT NULL) OR (:archived = false AND t.archivedAt IS NULL)) "
            + "AND (:status IS NULL OR t.status = :status) "
            + "AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId) "
            + "AND (CAST(:dueDateBefore AS date) IS NULL OR t.dueDate < :dueDateBefore) "
            + "AND (CAST(:dueDateAfter AS date) IS NULL OR t.dueDate > :dueDateAfter)")
    List<Task> findByFilters(@Param("projectId") UUID projectId,
                             @Param("status") TaskStatus status,
                             @Param("assigneeId") UUID assigneeId,
                             @Param("dueDateBefore") LocalDate dueDateBefore,
                             @Param("dueDateAfter") LocalDate dueDateAfter,
                             @Param("archived") boolean archived,
                             Sort sort);

    /**
     * Active task counts per status, for {@code docs/project/24-REPORTS-SPEC.md} §4 — the same
     * {@code GROUP BY} exception {@code ProjectRepository.countActiveByStatus} already
     * establishes as this module's one non-derived-name count query pattern.
     */
    @Query("SELECT t.status AS status, COUNT(t) AS total FROM Task t "
            + "WHERE t.projectId = :projectId AND t.archivedAt IS NULL GROUP BY t.status")
    List<StatusCount> countActiveByStatus(@Param("projectId") UUID projectId);

    /** Projection for {@link #countActiveByStatus}. */
    interface StatusCount {
        TaskStatus getStatus();

        long getTotal();
    }
}
