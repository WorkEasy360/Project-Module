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
    private static final String SECURITY_PACKAGE = "com.projectmodule.security";

    /**
     * Cross-origin callers the browser may let through. The packaged single-page app and its API are
     * the same origin (the Vite dev server proxies {@code /api} to keep that true in development), so
     * this list is not on the normal path. Credentials stay off: the session cookie is never sent
     * cross-origin, so a cross-origin page can read nothing that needs a sign-in. Origins are
     * configurable and never a wildcard.
     */
    @Value("${projectmodule.cors.allowed-origins:"
            + "http://localhost:5173,http://localhost:4173,"
            + "https://workeasy360.com,https://ops.workeasy360.com}")
    private String[] allowedOrigins;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Scoped to controllers in this module, leaving actuator and the OpenAPI endpoints
        // on their conventional paths. The sign-in controllers (security package) keep their own
        // /api/auth paths: they are not part of the versioned business API.
        HandlerTypePredicate moduleControllers = HandlerTypePredicate.forBasePackage(BASE_PACKAGE);
        configurer.addPathPrefix(API_PREFIX,
                type -> moduleControllers.test(type) && !type.getPackageName().startsWith(SECURITY_PACKAGE));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(API_PREFIX + "/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Correlation-Id")
                .exposedHeaders("Location");
    }
}
