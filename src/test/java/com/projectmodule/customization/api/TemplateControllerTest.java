package com.projectmodule.customization.api;

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
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.customization.application.ProjectTemplateApplicationService;
import com.projectmodule.customization.domain.ProjectTemplate;
import com.projectmodule.project.api.ProjectMapper;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
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

@WebMvcTest(TemplateController.class)
@Import({ApiConfiguration.class, TemplateMapper.class, ProjectMapper.class})
class TemplateControllerTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectTemplateApplicationService templateApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static ProjectTemplate sampleTemplate() {
        return ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.MEDIUM);
    }

    @Test
    @DisplayName("POST creates a template and returns 201 with a Location header")
    void createsTemplate() throws Exception {
        ProjectTemplate created = sampleTemplate();
        when(templateApplicationService.createTemplate(eq(requestContext), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Standard Sprint\",\"defaultPriority\":\"MEDIUM\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/templates/" + created.getId())))
                .andExpect(jsonPath("$.name").value("Standard Sprint"))
                .andExpect(jsonPath("$.defaultPriority").value("MEDIUM"));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST against a duplicate active name returns 409")
    void createDuplicateNameReturns409() throws Exception {
        when(templateApplicationService.createTemplate(eq(requestContext), any()))
                .thenThrow(new ConflictException("A template named \"Standard Sprint\" already exists in this organization"));

        mockMvc.perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Standard Sprint\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET list returns a page of templates")
    void listsTemplates() throws Exception {
        ProjectTemplate template = sampleTemplate();
        Pageable pageable = PageRequest.of(0, 20);
        when(templateApplicationService.listTemplates(eq(requestContext), any()))
                .thenReturn(new PageImpl<>(List.of(template), pageable, 1));

        mockMvc.perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Standard Sprint"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET by id returns the template")
    void getsTemplate() throws Exception {
        ProjectTemplate template = sampleTemplate();
        when(templateApplicationService.getTemplate(requestContext, template.getId())).thenReturn(template);

        mockMvc.perform(get("/api/v1/templates/{id}", template.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Standard Sprint"));
    }

    @Test
    @DisplayName("PATCH updates a template")
    void updatesTemplate() throws Exception {
        ProjectTemplate template = sampleTemplate();
        when(templateApplicationService.updateTemplate(eq(requestContext), eq(template.getId()), any()))
                .thenReturn(template);

        mockMvc.perform(patch("/api/v1/templates/{id}", template.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE archives the template and returns 204")
    void archivesTemplate() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/templates/{id}", id))
                .andExpect(status().isNoContent());

        verify(templateApplicationService).archiveTemplate(requestContext, id);
    }

    @Test
    @DisplayName("POST apply creates a project and returns 201 with a Location header pointing at the project")
    void appliesTemplate() throws Exception {
        ProjectTemplate template = sampleTemplate();
        Project createdProject = Project.create(ORG, "New Sprint Project", USER, ProjectPriority.MEDIUM);
        when(templateApplicationService.applyTemplate(eq(requestContext), eq(template.getId()), any()))
                .thenReturn(createdProject);

        mockMvc.perform(post("/api/v1/templates/{id}/apply", template.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Sprint Project\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/projects/" + createdProject.getId())))
                .andExpect(jsonPath("$.name").value("New Sprint Project"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    @DisplayName("POST apply with a blank name is rejected")
    void rejectsApplyWithBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/templates/{id}/apply", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
