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
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.DependencyApplicationService;
import com.projectmodule.work.domain.Dependency;
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

@WebMvcTest(DependencyController.class)
@Import({ApiConfiguration.class, DependencyMapper.class})
class DependencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DependencyApplicationService dependencyApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Dependency sampleDependency(UUID dependentTaskId, UUID prerequisiteTaskId) {
        return Dependency.create(dependentTaskId, prerequisiteTaskId);
    }

    @Test
    @DisplayName("POST creates a dependency and returns 201 with a Location header")
    void createsDependency() throws Exception {
        UUID dependentTaskId = UUID.randomUUID();
        UUID prerequisiteTaskId = UUID.randomUUID();
        Dependency created = sampleDependency(dependentTaskId, prerequisiteTaskId);
        when(dependencyApplicationService.createDependency(eq(requestContext), eq(dependentTaskId), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/tasks/{taskId}/dependencies", dependentTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prerequisiteTaskId\":\"" + prerequisiteTaskId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/dependencies/" + created.getId())))
                .andExpect(jsonPath("$.broken").value(false))
                .andExpect(jsonPath("$.prerequisiteTaskId").value(prerequisiteTaskId.toString()));
    }

    @Test
    @DisplayName("GET lists dependencies for the task")
    void listsDependencies() throws Exception {
        UUID dependentTaskId = UUID.randomUUID();
        Dependency dependency = sampleDependency(dependentTaskId, UUID.randomUUID());
        when(dependencyApplicationService.listDependencies(requestContext, dependentTaskId))
                .thenReturn(List.of(dependency));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/dependencies", dependentTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dependentTaskId").value(dependentTaskId.toString()));
    }

    @Test
    @DisplayName("PATCH sets broken")
    void updatesDependency() throws Exception {
        UUID dependentTaskId = UUID.randomUUID();
        Dependency dependency = sampleDependency(dependentTaskId, UUID.randomUUID());
        dependency.markBroken();
        when(dependencyApplicationService.updateDependency(eq(requestContext), eq(dependency.getId()), any()))
                .thenReturn(dependency);

        mockMvc.perform(patch("/api/v1/dependencies/{id}", dependency.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"broken\":true,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.broken").value(true));
    }

    @Test
    @DisplayName("DELETE archives the dependency and returns 204")
    void archivesDependency() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/dependencies/{id}", id))
                .andExpect(status().isNoContent());

        verify(dependencyApplicationService).archiveDependency(requestContext, id);
    }
}
