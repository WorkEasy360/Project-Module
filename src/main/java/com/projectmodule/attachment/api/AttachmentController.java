package com.projectmodule.attachment.api;

import com.projectmodule.attachment.application.AttachmentApplicationService;
import com.projectmodule.attachment.domain.Attachment;
import com.projectmodule.common.context.RequestContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Files on tasks. A download is always sent as a generic attachment, never with the uploaded file's
 * own type, so an uploaded HTML file cannot run as a page on this site.
 */
@RestController
public class AttachmentController {

    public record AttachmentResponse(UUID id, UUID taskId, String fileName, String contentType, long sizeBytes,
                                     UUID uploadedBy, Instant createdAt) {

        static AttachmentResponse from(Attachment attachment) {
            return new AttachmentResponse(attachment.getId(), attachment.getTaskId(), attachment.getFileName(),
                    attachment.getContentType(), attachment.getSizeBytes(), attachment.getUploadedBy(),
                    attachment.getCreatedAt());
        }
    }

    private final AttachmentApplicationService service;
    private final RequestContext requestContext;

    public AttachmentController(AttachmentApplicationService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @PostMapping(path = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(@PathVariable UUID taskId, @RequestParam("file") MultipartFile file)
            throws IOException {
        return AttachmentResponse.from(service.upload(requestContext, taskId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes()));
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public List<AttachmentResponse> list(@PathVariable UUID taskId) {
        return service.listForTask(requestContext, taskId).stream().map(AttachmentResponse::from).toList();
    }

    @GetMapping("/attachments/{id}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        AttachmentApplicationService.Download download = service.download(requestContext, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.attachment().getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.data());
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(requestContext, id);
    }
}
