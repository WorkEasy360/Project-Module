package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds projects and tasks by name, across only the projects the caller is a member of. */
@Service
public class GlobalSearchApplicationService {

    private static final int MAX_RESULTS = 25;
    private static final int MIN_TERM_LENGTH = 2;

    public record Hit(String type, UUID id, String name, UUID projectId, String projectName) {
    }

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public GlobalSearchApplicationService(ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<Hit> search(RequestContext context, String query) {
        String term = query == null ? "" : query.trim();
        if (term.length() < MIN_TERM_LENGTH) {
            throw new ValidationException("Enter at least " + MIN_TERM_LENGTH + " characters to search");
        }
        UUID userId = context.requireUserId().value();
        UUID organizationId = context.organizationId()
                .orElseThrow(() -> new MissingIdentityException("No organization was supplied with this request"))
                .value();
        PageRequest limit = PageRequest.of(0, MAX_RESULTS);

        List<Project> projects = projectRepository.searchVisibleToUser(organizationId, userId, term, limit);
        List<Object[]> tasks = taskRepository.searchVisibleToUser(organizationId, userId, term, limit);

        return Stream.concat(
                projects.stream().map(p -> new Hit("PROJECT", p.getId(), p.getName(), p.getId(), p.getName())),
                tasks.stream().map(row -> {
                    Task task = (Task) row[0];
                    return new Hit("TASK", task.getId(), task.getName(), task.getProjectId(), (String) row[1]);
                })).toList();
    }
}
