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

            People sign in with Workeasy360; the API is used with the server-side session that
            sign-in creates (an HttpOnly cookie, plus the X-XSRF-TOKEN header on writes). The
            session is not declared as an OpenAPI security scheme because it is established by
            a browser redirect flow, not by credentials sent to this API (docs/workeasy360-sso.md).

            Errors are returned as RFC 9457 problem documents.""";

    @Bean
    public OpenAPI projectModuleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Project Module API")
                .version("v1")
                .description(DESCRIPTION));
    }
}
