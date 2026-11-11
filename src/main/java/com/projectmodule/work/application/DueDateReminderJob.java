package com.projectmodule.work.application;

import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.notification.domain.NotificationType;
import com.projectmodule.notification.infrastructure.NotificationRepository;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each morning, tells the assignee of every open task due tomorrow. A recent reminder for the same
 * task is not repeated, so restarting the application cannot send a second one.
 */
@Component
public class DueDateReminderJob {

    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public DueDateReminderJob(TaskRepository taskRepository,
                              NotificationRepository notificationRepository,
                              NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${projectmodule.reminders.cron:0 0 3 * * *}", zone = "UTC")
    @Transactional
    public void sendReminders() {
        Instant now = Instant.now();
        LocalDate tomorrow = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1);
        for (Task task : taskRepository.findOpenAssignedTasksDueOn(tomorrow)) {
            task.getAssigneeId().ifPresent(assignee -> {
                boolean alreadyReminded = notificationRepository.existsByRecipientIdAndTaskIdAndTypeAndCreatedAtAfter(
                        assignee.value(), task.getId(), NotificationType.TASK_DUE_SOON, now.minus(Duration.ofHours(20)));
                if (!alreadyReminded) {
                    notificationService.notify(assignee.value(), NotificationType.TASK_DUE_SOON,
                            "\"" + task.getName() + "\" is due tomorrow", task.getProjectId(), task.getId());
                }
            });
        }
    }
}
