package com.projectmodule.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web layer conventions.
 *
 * <p>The version prefix is applied centrally rather than repeated on every controller, so it
 * cannot drift and a future version can be introduced in one place.
 */
@Configuration
public class ApiConfiguration implements WebMvcConfigurer {

    /** Every REST endpoint of this module is served beneath this prefix. */
    public static final String API_PREFIX = "/api/v1";

    private static final String BASE_PACKAGE = "com.projectmodule";

    /**
     * The React frontend (docs/project frontend/) is a separate origin from this backend, so
     * browsers require CORS to be enabled explicitly — without it every request from the SPA
     * would be blocked before reaching any controller, regardless of authorization. Origins are
     * configurable (never a wildcard, since requests carry the caller-identity headers below) and
     * default to the frontend's Vite dev/preview ports for local development.
     */
    @Value("${projectmodule.cors.allowed-origins:"
            + "http://localhost:5173,http://localhost:4173,"
            + "https://workeasy360.com,https://ops.workeasy360.com}")
    private String[] allowedOrigins;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Scoped to controllers in this module, leaving actuator and the OpenAPI endpoints
        // on their conventional paths.
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage(BASE_PACKAGE));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(API_PREFIX + "/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-User-Id", "X-Org-Id", "X-Correlation-Id")
                .exposedHeaders("Location");
    }
}
