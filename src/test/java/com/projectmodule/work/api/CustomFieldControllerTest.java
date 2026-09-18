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
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.ProjectCustomFieldApplicationService;
import com.projectmodule.work.domain.CustomFieldValueType;
import com.projectmodule.work.domain.ProjectCustomField;
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

@WebMvcTest(CustomFieldController.class)
@Import({ApiConfiguration.class, CustomFieldMapper.class})
class CustomFieldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCustomFieldApplicationService customFieldApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static ProjectCustomField sampleField(UUID projectId) {
        return ProjectCustomField.create(projectId, "Client Code", CustomFieldValueType.TEXT, "ACME-01");
    }

    @Test
    @DisplayName("POST creates a custom field and returns 201 with a Location header")
    void createsCustomField() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectCustomField created = sampleField(projectId);
        when(customFieldApplicationService.createCustomField(eq(requestContext), eq(projectId), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/custom-fields", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client Code\",\"valueType\":\"TEXT\",\"value\":\"ACME-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/custom-fields/" + created.getId())))
                .andExpect(jsonPath("$.name").value("Client Code"))
                .andExpect(jsonPath("$.valueType").value("TEXT"))
                .andExpect(jsonPath("$.value").value("ACME-01"));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/custom-fields", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"valueType\":\"TEXT\",\"value\":\"ACME-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST with no valueType is rejected")
    void rejectsMissingValueType() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/custom-fields", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client Code\",\"value\":\"ACME-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST with a blank value is rejected")
    void rejectsBlankValue() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/custom-fields", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client Code\",\"valueType\":\"TEXT\",\"value\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST against a duplicate active name returns 409")
    void createDuplicateNameReturns409() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(customFieldApplicationService.createCustomField(eq(requestContext), eq(projectId), any()))
                .thenThrow(new ConflictException("A custom field named \"Client Code\" already exists on this project"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/custom-fields", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client Code\",\"valueType\":\"TEXT\",\"value\":\"ACME-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/conflict"));
    }

    @Test
    @DisplayName("GET lists custom fields for the project")
    void listsCustomFields() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectCustomField field = sampleField(projectId);
        when(customFieldApplicationService.listCustomFields(requestContext, projectId)).thenReturn(List.of(field));

        mockMvc.perform(get("/api/v1/projects/{projectId}/custom-fields", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Client Code"));
    }

    @Test
    @DisplayName("PATCH updates a custom field")
    void updatesCustomField() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectCustomField field = sampleField(projectId);
        when(customFieldApplicationService.updateCustomField(eq(requestContext), eq(field.getId()), any()))
                .thenReturn(field);

        mockMvc.perform(patch("/api/v1/custom-fields/{id}", field.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"ACME-02\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH without a version is rejected")
    void rejectsUpdateWithoutVersion() throws Exception {
        mockMvc.perform(patch("/api/v1/custom-fields/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"ACME-02\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE archives the custom field and returns 204")
    void archivesCustomField() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/custom-fields/{id}", id))
                .andExpect(status().isNoContent());

        verify(customFieldApplicationService).archiveCustomField(requestContext, id);
    }
}
