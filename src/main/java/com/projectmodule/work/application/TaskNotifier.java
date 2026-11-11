package com.projectmodule.work.application;

import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.notification.domain.NotificationType;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskFollower;
import com.projectmodule.work.infrastructure.TaskFollowerRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Decides who hears about what happens to a task, and keeps its followers: the assignee and anyone
 * who comments follow it automatically. Nobody is told about their own action.
 */
@Service
public class TaskNotifier {

    private final NotificationService notificationService;
    private final TaskFollowerRepository followerRepository;
    private final UserAccountRepository accountRepository;

    public TaskNotifier(NotificationService notificationService,
                        TaskFollowerRepository followerRepository,
                        UserAccountRepository accountRepository) {
        this.notificationService = notificationService;
        this.followerRepository = followerRepository;
        this.accountRepository = accountRepository;
    }

    public void assigned(Task task, UUID actorId) {
        task.getAssigneeId().ifPresent(assignee -> {
            follow(task.getId(), assignee.value());
            if (!assignee.value().equals(actorId)) {
                notificationService.notify(assignee.value(), NotificationType.TASK_ASSIGNED,
                        nameOf(actorId) + " assigned you \"" + task.getName() + "\"",
                        task.getProjectId(), task.getId());
            }
        });
    }

    public void completed(Task task, UUID actorId) {
        for (UUID follower : followersExcept(task.getId(), actorId)) {
            notificationService.notify(follower, NotificationType.TASK_COMPLETED,
                    nameOf(actorId) + " completed \"" + task.getName() + "\"", task.getProjectId(), task.getId());
        }
    }

    /** People mentioned get a mention, which replaces the ordinary comment notice they would get. */
    public void commented(Task task, UUID actorId, Collection<UUID> mentioned) {
        follow(task.getId(), actorId);
        Set<UUID> others = new LinkedHashSet<>(followersExcept(task.getId(), actorId));
        for (UUID person : new LinkedHashSet<>(mentioned)) {
            if (!person.equals(actorId)) {
                others.remove(person);
                follow(task.getId(), person);
                notificationService.notify(person, NotificationType.MENTIONED,
                        nameOf(actorId) + " mentioned you on \"" + task.getName() + "\"",
                        task.getProjectId(), task.getId());
            }
        }
        for (UUID follower : others) {
            notificationService.notify(follower, NotificationType.TASK_COMMENTED,
                    nameOf(actorId) + " commented on \"" + task.getName() + "\"", task.getProjectId(), task.getId());
        }
    }

    /** A mention in the project's own discussion, which has no task to link to. */
    public void mentionedInProject(UUID projectId, UUID actorId, Collection<UUID> mentioned) {
        for (UUID person : new LinkedHashSet<>(mentioned)) {
            if (!person.equals(actorId)) {
                notificationService.notify(person, NotificationType.MENTIONED,
                        nameOf(actorId) + " mentioned you in a project discussion", projectId, null);
            }
        }
    }

    private void follow(UUID taskId, UUID userId) {
        TaskFollower.Key key = new TaskFollower.Key(taskId, userId);
        if (!followerRepository.existsById(key)) {
            followerRepository.save(new TaskFollower(taskId, userId));
        }
    }

    private Set<UUID> followersExcept(UUID taskId, UUID actorId) {
        Set<UUID> followers = new LinkedHashSet<>();
        for (TaskFollower follower : followerRepository.findByTaskId(taskId)) {
            if (!follower.getUserId().equals(actorId)) {
                followers.add(follower.getUserId());
            }
        }
        return followers;
    }

    private String nameOf(UUID userId) {
        return accountRepository.findById(userId).map(account -> account.getDisplayName()).orElse("Someone");
    }
}
