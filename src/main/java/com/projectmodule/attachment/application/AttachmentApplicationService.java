package com.projectmodule.attachment.application;

import com.projectmodule.attachment.domain.Attachment;
import com.projectmodule.attachment.domain.AttachmentContent;
import com.projectmodule.attachment.infrastructure.AttachmentContentRepository;
import com.projectmodule.attachment.infrastructure.AttachmentRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files on tasks. Anyone who can see a project can download its files; anyone who can work on its
 * tasks can add them. People remove their own uploads, and a project's managers can remove any.
 */
@Service
public class AttachmentApplicationService {

    /** The request limit is set to match, in {@code spring.servlet.multipart}. */
    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    public record Download(Attachment attachment, byte[] data) {
    }

    private final AttachmentRepository attachmentRepository;
    private final AttachmentContentRepository contentRepository;
    private final TaskApplicationService taskApplicationService;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public AttachmentApplicationService(AttachmentRepository attachmentRepository,
                                        AttachmentContentRepository contentRepository,
                                        TaskApplicationService taskApplicationService,
                                        ProjectApplicationService projectApplicationService,
                                        ProjectAuthorizationService projectAuthorizationService) {
        this.attachmentRepository = attachmentRepository;
        this.contentRepository = contentRepository;
        this.taskApplicationService = taskApplicationService;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public Attachment upload(RequestContext context, UUID taskId, String fileName, String contentType, byte[] data) {
        Task task = taskApplicationService.getTask(context, taskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_TASKS);
        if (data.length == 0) {
            throw new ValidationException("This file is empty");
        }
        if (data.length > MAX_FILE_BYTES) {
            throw new ValidationException("Files can be at most 25 MB");
        }
        Attachment attachment = attachmentRepository.saveAndFlush(Attachment.create(task.getId(), task.getProjectId(),
                fileName, contentType, data.length, context.requireUserId().value()));
        contentRepository.save(new AttachmentContent(attachment.getId(), data));
        return attachment;
    }

    @Transactional(readOnly = true)
    public List<Attachment> listForTask(RequestContext context, UUID taskId) {
        Task task = taskApplicationService.getTask(context, taskId);
        return attachmentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
    }

    @Transactional(readOnly = true)
    public Download download(RequestContext context, UUID attachmentId) {
        Attachment attachment = findVisible(context, attachmentId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), attachment.getProjectId(), ProjectPermission.VIEW_PROJECT);
        AttachmentContent content = contentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        return new Download(attachment, content.getData());
    }

    @Transactional
    public void delete(RequestContext context, UUID attachmentId) {
        Attachment attachment = findVisible(context, attachmentId);
        boolean own = attachment.getUploadedBy().equals(context.requireUserId().value());
        if (!own && !projectAuthorizationService.hasPermission(
                context.requireUserId(), attachment.getProjectId(), ProjectPermission.EDIT_PROJECT)) {
            throw new AuthorizationException("You can only remove files you uploaded");
        }
        attachmentRepository.delete(attachment);
    }

    private Attachment findVisible(RequestContext context, UUID attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        projectApplicationService.findActiveProjectInCallerOrganization(context, attachment.getProjectId());
        return attachment;
    }
}
