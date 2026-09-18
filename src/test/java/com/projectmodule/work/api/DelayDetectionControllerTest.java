package com.projectmodule.work.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.api.dto.DelayedItemResponse;
import com.projectmodule.work.application.DelayDetectionApplicationService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DelayDetectionController.class)
@Import(ApiConfiguration.class)
class DelayDetectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DelayDetectionApplicationService delayDetectionApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns delayed items")
    void returnsDelayedItems() throws Exception {
        UUID projectId = UUID.randomUUID();
        DelayedItemResponse item = new DelayedItemResponse(
                "TASK", UUID.randomUUID(), "Overdue task", LocalDate.of(2020, 1, 1), 100L);
        when(delayDetectionApplicationService.getDelayedItems(requestContext, projectId))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/projects/{projectId}/delayed", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].entityType").value("TASK"))
                .andExpect(jsonPath("$.items[0].name").value("Overdue task"))
                .andExpect(jsonPath("$.items[0].daysOverdue").value(100));
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(delayDetectionApplicationService.getDelayedItems(requestContext, projectId))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/delayed", projectId))
                .andExpect(status().isForbidden());
    }
}
