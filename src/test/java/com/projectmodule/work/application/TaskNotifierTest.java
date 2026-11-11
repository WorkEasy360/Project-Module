package com.projectmodule.work.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.notification.domain.NotificationType;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskFollower;
import com.projectmodule.work.infrastructure.TaskFollowerRepository;
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
    private TaskFollowerRepository followerRepository;

    @Mock
    private UserAccountRepository accountRepository;

    private TaskNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new TaskNotifier(notificationService, followerRepository, accountRepository);
        org.mockito.Mockito.lenient().when(accountRepository.findById(any())).thenReturn(Optional.empty());
    }

    private Task taskFollowedBy(UUID... followers) {
        Task task = Task.create(PROJECT, "Write report", null, null);
        when(followerRepository.findByTaskId(task.getId())).thenReturn(
                java.util.Arrays.stream(followers).map(id -> new TaskFollower(task.getId(), id)).toList());
        return task;
    }

    @Test
    @DisplayName("assigning someone else tells them, and they follow the task")
    void assignmentTellsTheAssignee() {
        Task task = Task.create(PROJECT, "Write report", null, ExternalUserId.of(OTHER));

        notifier.assigned(task, ACTOR);

        verify(notificationService).notify(eq(OTHER), eq(NotificationType.TASK_ASSIGNED), any(), eq(PROJECT), eq(task.getId()));
        verify(followerRepository).save(any(TaskFollower.class));
    }

    @Test
    @DisplayName("nobody is told about assigning themselves")
    void selfAssignmentIsSilent() {
        Task task = Task.create(PROJECT, "Write report", null, ExternalUserId.of(ACTOR));

        notifier.assigned(task, ACTOR);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("completing a task tells its followers, but not whoever completed it")
    void completionTellsFollowersOnly() {
        Task task = taskFollowedBy(ACTOR, OTHER);

        notifier.completed(task, ACTOR);

        verify(notificationService).notify(eq(OTHER), eq(NotificationType.TASK_COMPLETED), any(), any(), any());
        verify(notificationService, never()).notify(eq(ACTOR), any(), any(), any(), any());
    }

    @Test
    @DisplayName("someone mentioned gets one notice, the mention, not a comment notice too")
    void mentionReplacesTheCommentNotice() {
        UUID thirdPerson = UUID.randomUUID();
        Task task = taskFollowedBy(ACTOR, OTHER, thirdPerson);

        notifier.commented(task, ACTOR, List.of(OTHER));

        verify(notificationService, times(1)).notify(eq(OTHER), eq(NotificationType.MENTIONED), any(), any(), any());
        verify(notificationService, never()).notify(eq(OTHER), eq(NotificationType.TASK_COMMENTED), any(), any(), any());
        verify(notificationService).notify(eq(thirdPerson), eq(NotificationType.TASK_COMMENTED), any(), any(), any());
    }

    @Test
    @DisplayName("mentioning yourself tells nobody")
    void selfMentionIsSilent() {
        Task task = taskFollowedBy();

        notifier.commented(task, ACTOR, List.of(ACTOR));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
