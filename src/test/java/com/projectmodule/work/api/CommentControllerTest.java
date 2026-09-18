package com.projectmodule.work.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        return ProjectComment.create(projectId, ExternalUserId.of(UUID.randomUUID()), "Looks good to me");
    }

    @Test
    @DisplayName("POST creates a comment and returns 201 with a Location header")
    void createsComment() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectComment created = sampleComment(projectId);
        when(commentApplicationService.createComment(eq(requestContext), eq(projectId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/comments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Looks good to me\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/comments/" + created.getId())))
                .andExpect(jsonPath("$.body").value("Looks good to me"));
    }

    @Test
    @DisplayName("POST with a blank body is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/comments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET returns a page of comments, oldest-first by default")
    void listsComments() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectComment comment = sampleComment(projectId);
        Pageable pageable = PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "createdAt"));
        when(commentApplicationService.listComments(eq(requestContext), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(comment), pageable, 1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/comments", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value("Looks good to me"))
                .andExpect(jsonPath("$.totalElements").value(1));
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
