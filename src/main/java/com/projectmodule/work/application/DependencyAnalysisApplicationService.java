package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.DependencyMapper;
import com.projectmodule.work.api.dto.DependencyAnalysisResponse;
import com.projectmodule.work.api.dto.DependencyResponse;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.DependencyRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's dependency graph analysis — cycles, blocked tasks and broken dependencies — per
 * the approved {@code docs/project/23-DEPENDENCY-ANALYSIS-SPEC.md}. Read-only: this does not
 * change {@code DependencyApplicationService.createDependency}'s existing validation (§7).
 *
 * <p>Same authorization pattern as every other project-scoped read. {@code Dependency} has no
 * {@code projectId} column, so the project's active task ids are resolved first
 * ({@link TaskRepository#findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc}), then every
 * active dependency touching those tasks is fetched in one query
 * ({@link DependencyRepository#findByDependentTaskIdInAndArchivedAtIsNull}) — the same
 * indirection {@code DependencyApplicationService} already documents as necessary.
 */
@Service
public class DependencyAnalysisApplicationService {

    private final TaskRepository taskRepository;
    private final DependencyRepository dependencyRepository;
    private final DependencyMapper dependencyMapper;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public DependencyAnalysisApplicationService(TaskRepository taskRepository,
                                                DependencyRepository dependencyRepository,
                                                DependencyMapper dependencyMapper,
                                                ProjectApplicationService projectApplicationService,
                                                ProjectAuthorizationService projectAuthorizationService) {
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.dependencyMapper = dependencyMapper;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public DependencyAnalysisResponse getAnalysis(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        List<Task> projectTasks = taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
        Set<UUID> projectTaskIds = projectTasks.stream().map(Task::getId).collect(Collectors.toSet());
        if (projectTaskIds.isEmpty()) {
            return new DependencyAnalysisResponse(List.of(), List.of(), List.of());
        }

        List<Dependency> dependencies = dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(projectTaskIds);

        Set<UUID> prerequisiteIds = dependencies.stream().map(Dependency::getPrerequisiteTaskId)
                .collect(Collectors.toSet());
        Map<UUID, Task> tasksById = taskRepository.findAllById(prerequisiteIds).stream()
                .collect(Collectors.toMap(Task::getId, task -> task));

        List<Dependency> activeUnbroken = dependencies.stream().filter(d -> !d.isBroken()).toList();

        List<UUID> blockedTaskIds = activeUnbroken.stream()
                .filter(d -> isUnresolved(tasksById.get(d.getPrerequisiteTaskId())))
                .map(Dependency::getDependentTaskId)
                .distinct()
                .toList();

        List<DependencyResponse> brokenDependencies = dependencies.stream()
                .filter(Dependency::isBroken)
                .map(dependencyMapper::toResponse)
                .toList();

        List<List<UUID>> cycles = findCycles(activeUnbroken);

        return new DependencyAnalysisResponse(cycles, blockedTaskIds, brokenDependencies);
    }

    /** A missing (never-loaded) or archived-and-not-completed prerequisite still blocks. */
    private static boolean isUnresolved(Task prerequisite) {
        return prerequisite == null || prerequisite.getStatus() != TaskStatus.COMPLETED;
    }

    /** Standard DFS-based cycle detection over the {@code dependentTaskId -> prerequisiteTaskId} edges. */
    private static List<List<UUID>> findCycles(List<Dependency> edges) {
        Map<UUID, List<UUID>> graph = new HashMap<>();
        for (Dependency edge : edges) {
            graph.computeIfAbsent(edge.getDependentTaskId(), k -> new ArrayList<>()).add(edge.getPrerequisiteTaskId());
        }

        List<List<UUID>> cycles = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        Set<UUID> inStack = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();

        for (UUID start : graph.keySet()) {
            if (!visited.contains(start)) {
                depthFirstSearch(start, graph, visited, inStack, stack, cycles);
            }
        }
        return cycles;
    }

    private static void depthFirstSearch(UUID node, Map<UUID, List<UUID>> graph, Set<UUID> visited,
                                         Set<UUID> inStack, Deque<UUID> stack, List<List<UUID>> cycles) {
        visited.add(node);
        inStack.add(node);
        stack.push(node);

        for (UUID next : graph.getOrDefault(node, List.of())) {
            if (inStack.contains(next)) {
                cycles.add(extractCycle(stack, next));
            } else if (!visited.contains(next)) {
                depthFirstSearch(next, graph, visited, inStack, stack, cycles);
            }
        }

        stack.pop();
        inStack.remove(node);
    }

    private static List<UUID> extractCycle(Deque<UUID> stack, UUID cycleStart) {
        List<UUID> cycle = new ArrayList<>();
        for (UUID node : stack) {
            cycle.add(node);
            if (node.equals(cycleStart)) {
                break;
            }
        }
        List<UUID> ordered = new ArrayList<>(cycle);
        Collections.reverse(ordered);
        return ordered;
    }
}
