package com.projectmodule.work.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.ProjectCommentApplicationService;
import com.projectmodule.work.domain.ProjectComment;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommentController.class)
@Import({ApiConfiguration.class, CommentMapper.class})
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCommentApplicationService commentApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static ProjectComment sampleComment(UUID projectId) {
        return ProjectComment.createOnTask(projectId, UUID.randomUUID(), ExternalUserId.of(UUID.randomUUID()),
                "Looks good to me");
    }

    @Test
    @DisplayName("POST on a task creates a comment and returns 201")
    void createsTaskComment() throws Exception {
        UUID taskId = UUID.randomUUID();
        ProjectComment created = sampleComment(UUID.randomUUID());
        when(commentApplicationService.createTaskComment(eq(requestContext), eq(taskId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Looks good to me\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Looks good to me"))
                .andExpect(jsonPath("$.taskId").value(created.getTaskId().toString()));
    }

    @Test
    @DisplayName("POST with a blank body is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/{taskId}/comments", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lists a task's comments, oldest first")
    void listsTaskComments() throws Exception {
        UUID taskId = UUID.randomUUID();
        ProjectComment comment = sampleComment(UUID.randomUUID());
        when(commentApplicationService.listTaskComments(requestContext, taskId)).thenReturn(List.of(comment));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/comments", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Looks good to me"));
    }

    @Test
    @DisplayName("project-level comments are gone")
    void projectLevelCommentsAreGone() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/comments", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hello\"}"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .isIn(404, 405));
    }

    @Test
    @DisplayName("PATCH updates a comment")
    void updatesComment() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectComment comment = sampleComment(projectId);
        when(commentApplicationService.updateComment(eq(requestContext), eq(comment.getId()), any()))
                .thenReturn(comment);

        mockMvc.perform(patch("/api/v1/comments/{id}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Updated\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH without a version is rejected")
    void rejectsUpdateWithoutVersion() throws Exception {
        mockMvc.perform(patch("/api/v1/comments/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Updated\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH by a non-author returns the standard 403")
    void updateByNonAuthorReturns403() throws Exception {
        UUID id = UUID.randomUUID();
        when(commentApplicationService.updateComment(eq(requestContext), eq(id), any()))
                .thenThrow(new AuthorizationException("Only the comment's author may edit it"));

        mockMvc.perform(patch("/api/v1/comments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Updated\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE archives the comment and returns 204")
    void archivesComment() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/comments/{id}", id))
                .andExpect(status().isNoContent());

        verify(commentApplicationService).archiveComment(requestContext, id);
    }
}
