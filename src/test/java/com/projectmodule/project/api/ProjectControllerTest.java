package com.projectmodule.project.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.api.GlobalExceptionHandler;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Controller slice test: routing, request/response serialization and error mapping.
 *
 * <p>Domain rules and application-layer logic are covered by {@code ProjectTest} and
 * {@code ProjectApplicationServiceTest}; this test does not repeat them; it verifies that the
 * controller wires HTTP correctly onto the application service.
 *
 * <p>{@link ApiConfiguration} (the {@code /api/v1} prefix) and {@link ProjectMapper} are
 * imported explicitly rather than relied on to be auto-detected by the slice, so the test does
 * not depend on {@code @WebMvcTest}'s component-scanning specifics. {@link GlobalExceptionHandler}
 * is a {@code @RestControllerAdvice} and is picked up by the slice automatically.
 */
@WebMvcTest(ProjectController.class)
@Import({ApiConfiguration.class, ProjectMapper.class})
class ProjectControllerTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId OWNER = ExternalUserId.of(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProjectApplicationService projectApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @BeforeEach
    void setUp() {
        when(requestContext.correlationId()).thenReturn("corr-test");
    }

    private static Project sampleProject() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);
        project.describe("Lunar programme");
        return project;
    }

    @Test
    @DisplayName("POST creates a project and returns 201 with a Location header")
    void createsProject() throws Exception {
        Project created = sampleProject();
        when(projectApplicationService.createProject(eq(requestContext), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apollo","description":"Lunar programme","priority":"HIGH"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/projects/" + created.getId())))
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value("Apollo"))
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    @DisplayName("POST with a blank name is rejected before reaching the application layer")
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   ","priority":"HIGH"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        verify(projectApplicationService, org.mockito.Mockito.never()).createProject(any(), any());
    }

    @Test
    @DisplayName("POST with no priority is rejected")
    void rejectsMissingPriority() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apollo"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET by id returns the project")
    void getsProject() throws Exception {
        Project project = sampleProject();
        when(projectApplicationService.getProject(eq(requestContext), eq(project.getId())))
                .thenReturn(project);

        mockMvc.perform(get("/api/v1/projects/{id}", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Apollo"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("GET by id returns the standard not-found error when the project does not exist")
    void getMissingProjectReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectApplicationService.getProject(eq(requestContext), eq(id)))
                .thenThrow(new ResourceNotFoundException("Project", id));

        mockMvc.perform(get("/api/v1/projects/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/not-found"));
    }

    @Test
    @DisplayName("GET list returns a page of project summaries")
    void listsProjects() throws Exception {
        Project project = sampleProject();
        Pageable pageable = PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        when(projectApplicationService.listProjects(eq(requestContext), any()))
                .thenReturn(new PageImpl<>(List.of(project), pageable, 1));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Apollo"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                // The summary view omits fields the full response carries.
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    @DisplayName("PATCH applies the update and returns the result")
    void updatesProject() throws Exception {
        Project project = sampleProject();
        when(projectApplicationService.updateProject(eq(requestContext), eq(project.getId()), any()))
                .thenReturn(project);

        mockMvc.perform(patch("/api/v1/projects/{id}", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Apollo Renamed\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Apollo"));
    }

    @Test
    @DisplayName("PATCH without a version is rejected")
    void rejectsUpdateWithoutVersion() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Apollo Renamed\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH against a stale version returns 409")
    void updateConflictReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectApplicationService.updateProject(eq(requestContext), eq(id), any()))
                .thenThrow(new ConflictException("Project " + id + " has changed since version 0"));

        mockMvc.perform(patch("/api/v1/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Apollo Renamed\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/conflict"));
    }

    @Test
    @DisplayName("DELETE archives the project and returns 204")
    void archivesProject() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/projects/{id}", id))
                .andExpect(status().isNoContent());

        verify(projectApplicationService).archiveProject(requestContext, id);
    }

    @Test
    @DisplayName("DELETE on a missing project returns the standard not-found error")
    void archiveMissingProjectReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Project", id))
                .when(projectApplicationService).archiveProject(requestContext, id);

        mockMvc.perform(delete("/api/v1/projects/{id}", id))
                .andExpect(status().isNotFound());
    }
}
