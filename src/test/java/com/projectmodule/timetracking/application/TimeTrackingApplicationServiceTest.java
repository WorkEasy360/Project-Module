package com.projectmodule.timetracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.timetracking.domain.ActiveTimer;
import com.projectmodule.timetracking.domain.TimeEntry;
import com.projectmodule.timetracking.infrastructure.ActiveTimerRepository;
import com.projectmodule.timetracking.infrastructure.TimeEntryRepository;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeTrackingApplicationServiceTest {

    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());
    private static final UUID PROJECT = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);

    @Mock
    private TimeEntryRepository timeEntryRepository;

    @Mock
    private ActiveTimerRepository activeTimerRepository;

    @Mock
    private TaskApplicationService taskApplicationService;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService authorization;

    private TimeTrackingApplicationService service;
    private RequestContext context;
    private Task task;

    @BeforeEach
    void setUp() {
        service = new TimeTrackingApplicationService(timeEntryRepository, activeTimerRepository,
                taskApplicationService, projectApplicationService, authorization);
        context = new ImmutableRequestContext(Optional.of(USER),
                Optional.of(OrganizationId.of(UUID.randomUUID())), "corr", ActorType.HUMAN);
        task = Task.create(PROJECT, "Write report", null, null);
        lenient().when(taskApplicationService.getTask(context, task.getId())).thenReturn(task);
        lenient().when(timeEntryRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("time is logged as the caller, on the task's project")
    void logsAsTheCaller() {
        TimeEntry entry = service.logTime(context, task.getId(), TODAY, 90, " Drafting ", true);

        assertThat(entry.getUserId()).isEqualTo(USER.value());
        assertThat(entry.getProjectId()).isEqualTo(PROJECT);
        assertThat(entry.getMinutes()).isEqualTo(90);
        assertThat(entry.getNote()).contains("Drafting");
        assertThat(entry.isBillable()).isTrue();
    }

    @Test
    @DisplayName("someone who cannot work on tasks cannot log time")
    void requiresTaskPermission() {
        doThrow(new AuthorizationException("denied")).when(authorization)
                .requirePermission(USER, PROJECT, ProjectPermission.EDIT_TASKS);

        assertThatThrownBy(() -> service.logTime(context, task.getId(), TODAY, 30, null, false))
                .isInstanceOf(AuthorizationException.class);
        verify(timeEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("an entry must be more than nothing and at most a day")
    void checksTheAmount() {
        assertThatThrownBy(() -> service.logTime(context, task.getId(), TODAY, 0, null, false))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.logTime(context, task.getId(), TODAY, 1441, null, false))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("a second timer is refused while one is running")
    void oneTimerAtATime() {
        when(activeTimerRepository.existsById(USER.value())).thenReturn(true);

        assertThatThrownBy(() -> service.startTimer(context, task.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("stopping a timer logs the time on the person's date and ends the timer")
    void stoppingLogsTime() {
        ActiveTimer timer = new ActiveTimer(USER.value(), task.getId(), Instant.now().minusSeconds(90));
        when(activeTimerRepository.findById(USER.value())).thenReturn(Optional.of(timer));

        TimeEntry entry = service.stopTimer(context, TODAY, null, false);

        assertThat(entry.getMinutes()).isEqualTo(2);
        assertThat(entry.getWorkDate()).isEqualTo(TODAY);
        verify(activeTimerRepository).delete(timer);
    }

    @Test
    @DisplayName("a timer that ran for seconds still logs a minute")
    void shortTimerLogsAMinute() {
        ActiveTimer timer = new ActiveTimer(USER.value(), task.getId(), Instant.now());
        when(activeTimerRepository.findById(USER.value())).thenReturn(Optional.of(timer));

        assertThat(service.stopTimer(context, TODAY, null, false).getMinutes()).isEqualTo(1);
    }

    @Test
    @DisplayName("a saved timer can be deleted: Spring Data skips deleting anything it thinks is new")
    void savedTimerIsNotNew() throws Exception {
        ActiveTimer timer = new ActiveTimer(USER.value(), task.getId(), Instant.now());
        assertThat(timer.isNew()).isTrue();
        java.lang.reflect.Method markNotNew = ActiveTimer.class.getDeclaredMethod("markNotNew");
        markNotNew.setAccessible(true);
        markNotNew.invoke(timer);
        assertThat(timer.isNew()).isFalse();
    }

    @Test
    @DisplayName("someone else's entry cannot be removed without project rights")
    void cannotRemoveOthersEntries() {
        TimeEntry other = TimeEntry.log(task.getId(), PROJECT, UUID.randomUUID(), TODAY, 30, null, false);
        when(timeEntryRepository.findById(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.deleteEntry(context, other.getId()))
                .isInstanceOf(AuthorizationException.class);
        verify(timeEntryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a timesheet range is limited, and must run forwards")
    void checksTheRange() {
        assertThatThrownBy(() -> service.listMine(context, TODAY, TODAY.minusDays(1)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.listMine(context, TODAY, TODAY.plusYears(1)))
                .isInstanceOf(ValidationException.class);
    }
}
