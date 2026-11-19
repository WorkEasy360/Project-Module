package com.projectmodule.work.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.notification.domain.NotificationType;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.ProjectComment;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.ProjectCommentRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskNotifierTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserAccountRepository accountRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectCommentRepository commentRepository;

    private TaskNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new TaskNotifier(notificationService, accountRepository, projectRepository, commentRepository);
        org.mockito.Mockito.lenient().when(accountRepository.findById(any())).thenReturn(Optional.empty());
    }

    private Task taskCommentedOnBy(UUID... authors) {
        Task task = Task.create(PROJECT, "Write report", null, null);
        when(commentRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(task.getId())).thenReturn(
                Arrays.stream(authors)
                        .map(id -> ProjectComment.createOnTask(PROJECT, task.getId(), ExternalUserId.of(id), "Noted"))
                        .toList());
        return task;
    }

    @Test
    @DisplayName("assigning someone else tells them")
    void assignmentTellsTheAssignee() {
        Task task = Task.create(PROJECT, "Write report", null, ExternalUserId.of(OTHER));

        notifier.assigned(task, ACTOR);

        verify(notificationService).notify(eq(OTHER), eq(NotificationType.TASK_ASSIGNED), any(), eq(PROJECT), eq(task.getId()));
    }

    @Test
    @DisplayName("nobody is told about assigning themselves")
    void selfAssignmentIsSilent() {
        Task task = Task.create(PROJECT, "Write report", null, ExternalUserId.of(ACTOR));

        notifier.assigned(task, ACTOR);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("completing a task tells its assignee and the project owner, but not whoever completed it")
    void completionTellsAssigneeAndOwner() {
        UUID owner = UUID.randomUUID();
        Project project = Project.create(OrganizationId.of(UUID.randomUUID()), "Apollo", ExternalUserId.of(owner),
                ProjectPriority.MEDIUM);
        Task task = Task.create(project.getId(), "Write report", null, ExternalUserId.of(OTHER));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        notifier.completed(task, ACTOR);

        verify(notificationService).notify(eq(OTHER), eq(NotificationType.TASK_COMPLETED), any(), any(), any());
        verify(notificationService).notify(eq(owner), eq(NotificationType.TASK_COMPLETED), any(), any(), any());
        verify(notificationService, never()).notify(eq(ACTOR), any(), any(), any(), any());
    }

    @Test
    @DisplayName("someone mentioned gets one notice, the mention, not a comment notice too")
    void mentionReplacesTheCommentNotice() {
        UUID thirdPerson = UUID.randomUUID();
        Task task = taskCommentedOnBy(ACTOR, OTHER, thirdPerson);

        notifier.commented(task, ACTOR, List.of(OTHER));

        verify(notificationService, times(1)).notify(eq(OTHER), eq(NotificationType.MENTIONED), any(), any(), any());
        verify(notificationService, never()).notify(eq(OTHER), eq(NotificationType.TASK_COMMENTED), any(), any(), any());
        verify(notificationService).notify(eq(thirdPerson), eq(NotificationType.TASK_COMMENTED), any(), any(), any());
        verify(notificationService, never()).notify(eq(ACTOR), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a comment tells the task's assignee")
    void commentTellsTheAssignee() {
        Task task = Task.create(PROJECT, "Write report", null, ExternalUserId.of(OTHER));
        when(commentRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(task.getId())).thenReturn(List.of());

        notifier.commented(task, ACTOR, List.of());

        verify(notificationService).notify(eq(OTHER), eq(NotificationType.TASK_COMMENTED), any(), any(), any());
    }

    @Test
    @DisplayName("mentioning yourself tells nobody")
    void selfMentionIsSilent() {
        Task task = taskCommentedOnBy();

        notifier.commented(task, ACTOR, List.of(ACTOR));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
