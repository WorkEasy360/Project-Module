package com.projectmodule.project.api;

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

import com.projectmodule.common.api.GlobalExceptionHandler;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.project.application.ProjectMemberApplicationService;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller slice test for project membership endpoints: routing, serialization and error
 * mapping. Authorization and membership rules are covered by
 * {@code ProjectMemberApplicationServiceTest}.
 */
@WebMvcTest(ProjectMemberController.class)
@Import({ApiConfiguration.class, ProjectMemberMapper.class})
class ProjectMemberControllerTest {

    private static final ExternalUserId CALLER = ExternalUserId.of(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectMemberApplicationService projectMemberApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static ProjectMember sampleMember(UUID projectId) {
        return ProjectMember.join(projectId, CALLER, ProjectRole.MEMBER);
    }

    @Test
    @DisplayName("GET lists members of the project")
    void listsMembers() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectMember member = sampleMember(projectId);
        when(projectMemberApplicationService.listMembers(eq(requestContext), eq(projectId)))
                .thenReturn(List.of(member));

        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(CALLER.value().toString()))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));
    }

    @Test
    @DisplayName("POST adds a member and returns 201 with a Location header")
    void addsMember() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectMember member = sampleMember(projectId);
        when(projectMemberApplicationService.addMember(eq(requestContext), eq(projectId), any()))
                .thenReturn(member);

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + CALLER.value() + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/members/" + member.getId())))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    @DisplayName("POST with a missing userId is rejected")
    void rejectsInvalidAddRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST for an already-forbidden caller returns the standard authorization error")
    void addMemberForbidden() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectMemberApplicationService.addMember(eq(requestContext), eq(projectId), any()))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/forbidden"));
    }

    @Test
    @DisplayName("POST for a duplicate member returns 409")
    void addMemberConflict() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectMemberApplicationService.addMember(eq(requestContext), eq(projectId), any()))
                .thenThrow(new ConflictException("already a member"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH changes the member's role")
    void changesRole() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ProjectMember updated = sampleMember(projectId);
        when(projectMemberApplicationService.changeMemberRole(eq(requestContext), eq(projectId), eq(memberId), any()))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/projects/{projectId}/members/{memberId}", projectId, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH assigning OWNER returns the standard business-rule error")
    void changeRoleToOwnerRejected() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(projectMemberApplicationService.changeMemberRole(eq(requestContext), eq(projectId), eq(memberId), any()))
                .thenThrow(new BusinessRuleViolationException("not supported"));

        mockMvc.perform(patch("/api/v1/projects/{projectId}/members/{memberId}", projectId, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/business-rule-violation"));
    }

    @Test
    @DisplayName("DELETE removes the member and returns 204")
    void removesMember() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{memberId}", projectId, memberId))
                .andExpect(status().isNoContent());

        verify(projectMemberApplicationService).removeMember(requestContext, projectId, memberId);
    }
}
