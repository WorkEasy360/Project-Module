package com.projectmodule.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata.
 *
 * <p>The generated document is the contract handed to the frontend and to any other module
 * that calls this one.
 */
@Configuration
public class OpenApiConfiguration {

    private static final String DESCRIPTION = """
            Project planning, execution and tracking.

            This module owns project data only. Users, organizations, documents, calendars,
            notifications and chat are owned by other modules and are referenced here by
            opaque identifier.

            Caller identity is supplied by the upstream gateway in the X-User-Id and X-Org-Id
            headers. These are a trust contract with the gateway, not an authentication scheme
            offered by this module, and are deliberately not declared as an OpenAPI security
            scheme.

            Errors are returned as RFC 9457 problem documents.""";

    @Bean
    public OpenAPI projectModuleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Project Module API")
                .version("v1")
                .description(DESCRIPTION));
    }
}
