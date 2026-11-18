package com.projectmodule.work.application;

import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.notification.domain.NotificationType;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.ProjectComment;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.ProjectCommentRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Decides who hears about what happens to a task. The new assignee hears about an assignment; the
 * assignee and the project owner hear about completion; the assignee and earlier commenters hear
 * about a new comment, and anyone mentioned gets a mention instead. Nobody is told about their own
 * action.
 */
@Service
public class TaskNotifier {

    private final NotificationService notificationService;
    private final UserAccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final ProjectCommentRepository commentRepository;

    public TaskNotifier(NotificationService notificationService,
                        UserAccountRepository accountRepository,
                        ProjectRepository projectRepository,
                        ProjectCommentRepository commentRepository) {
        this.notificationService = notificationService;
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.commentRepository = commentRepository;
    }

    public void assigned(Task task, UUID actorId) {
        task.getAssigneeId().ifPresent(assignee -> {
            if (!assignee.value().equals(actorId)) {
                notificationService.notify(assignee.value(), NotificationType.TASK_ASSIGNED,
                        nameOf(actorId) + " assigned you \"" + task.getName() + "\"",
                        task.getProjectId(), task.getId());
            }
        });
    }

    public void completed(Task task, UUID actorId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        task.getAssigneeId().ifPresent(assignee -> recipients.add(assignee.value()));
        projectRepository.findById(task.getProjectId())
                .ifPresent(project -> recipients.add(project.getOwnerId().value()));
        recipients.remove(actorId);
        for (UUID person : recipients) {
            notificationService.notify(person, NotificationType.TASK_COMPLETED,
                    nameOf(actorId) + " completed \"" + task.getName() + "\"", task.getProjectId(), task.getId());
        }
    }

    /** People mentioned get a mention, which replaces the ordinary comment notice they would get. */
    public void commented(Task task, UUID actorId, Collection<UUID> mentioned) {
        Set<UUID> others = new LinkedHashSet<>();
        task.getAssigneeId().ifPresent(assignee -> others.add(assignee.value()));
        for (ProjectComment earlier : commentRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(task.getId())) {
            others.add(earlier.getAuthorId().value());
        }
        others.remove(actorId);
        for (UUID person : new LinkedHashSet<>(mentioned)) {
            if (!person.equals(actorId)) {
                others.remove(person);
                notificationService.notify(person, NotificationType.MENTIONED,
                        nameOf(actorId) + " mentioned you on \"" + task.getName() + "\"",
                        task.getProjectId(), task.getId());
            }
        }
        for (UUID person : others) {
            notificationService.notify(person, NotificationType.TASK_COMMENTED,
                    nameOf(actorId) + " commented on \"" + task.getName() + "\"", task.getProjectId(), task.getId());
        }
    }

    private String nameOf(UUID userId) {
        return accountRepository.findById(userId).map(account -> account.getDisplayName()).orElse("Someone");
    }
}
