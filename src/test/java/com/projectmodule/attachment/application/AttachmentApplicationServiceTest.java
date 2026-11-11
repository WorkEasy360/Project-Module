package com.projectmodule.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.attachment.domain.Attachment;
import com.projectmodule.attachment.infrastructure.AttachmentContentRepository;
import com.projectmodule.attachment.infrastructure.AttachmentRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentApplicationServiceTest {

    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());
    private static final UUID PROJECT = UUID.randomUUID();

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentContentRepository contentRepository;

    @Mock
    private TaskApplicationService taskApplicationService;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService authorization;

    private AttachmentApplicationService service;
    private RequestContext context;
    private Task task;

    @BeforeEach
    void setUp() {
        service = new AttachmentApplicationService(attachmentRepository, contentRepository, taskApplicationService,
                projectApplicationService, authorization);
        context = new ImmutableRequestContext(Optional.of(USER),
                Optional.of(OrganizationId.of(UUID.randomUUID())), "corr", ActorType.HUMAN);
        task = Task.create(PROJECT, "Write report", null, null);
        lenient().when(taskApplicationService.getTask(context, task.getId())).thenReturn(task);
        lenient().when(attachmentRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("an upload keeps only the file's own name, whatever path a browser sends")
    void keepsOnlyTheFileName() {
        Attachment attachment = service.upload(context, task.getId(), "C:\\Users\\me\\..\\report.pdf",
                "application/pdf", new byte[] {1});

        assertThat(attachment.getFileName()).isEqualTo("report.pdf");
        assertThat(attachment.getSizeBytes()).isEqualTo(1);
        assertThat(attachment.getUploadedBy()).isEqualTo(USER.value());
        verify(contentRepository).save(any());
    }

    @Test
    @DisplayName("line breaks are removed from a file name, so it cannot forge a response header")
    void removesControlCharacters() {
        Attachment attachment = service.upload(context, task.getId(), "a\r\nb.txt", "text/plain", new byte[] {1});

        assertThat(attachment.getFileName()).isEqualTo("ab.txt");
    }

    @Test
    @DisplayName("an empty file and an oversized file are refused")
    void checksSize() {
        assertThatThrownBy(() -> service.upload(context, task.getId(), "a.txt", "text/plain", new byte[0]))
                .isInstanceOf(ValidationException.class);
        byte[] tooBig = new byte[(int) AttachmentApplicationService.MAX_FILE_BYTES + 1];
        assertThatThrownBy(() -> service.upload(context, task.getId(), "a.txt", "text/plain", tooBig))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("someone who cannot work on tasks cannot upload")
    void requiresTaskPermission() {
        doThrow(new AuthorizationException("denied")).when(authorization)
                .requirePermission(USER, PROJECT, ProjectPermission.EDIT_TASKS);

        assertThatThrownBy(() -> service.upload(context, task.getId(), "a.txt", "text/plain", new byte[] {1}))
                .isInstanceOf(AuthorizationException.class);
        verify(attachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("downloading requires being able to see the project")
    void downloadRequiresViewPermission() {
        Attachment attachment = Attachment.create(task.getId(), PROJECT, "a.txt", "text/plain", 1, UUID.randomUUID());
        when(attachmentRepository.findById(attachment.getId())).thenReturn(Optional.of(attachment));
        doThrow(new AuthorizationException("denied")).when(authorization)
                .requirePermission(USER, PROJECT, ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.download(context, attachment.getId()))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("people remove their own files; removing someone else's needs project rights")
    void deletionRules() {
        Attachment others = Attachment.create(task.getId(), PROJECT, "a.txt", "text/plain", 1, UUID.randomUUID());
        when(attachmentRepository.findById(others.getId())).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> service.delete(context, others.getId())).isInstanceOf(AuthorizationException.class);

        when(authorization.hasPermission(USER, PROJECT, ProjectPermission.EDIT_PROJECT)).thenReturn(true);
        service.delete(context, others.getId());
        verify(attachmentRepository).delete(others);
    }
}
