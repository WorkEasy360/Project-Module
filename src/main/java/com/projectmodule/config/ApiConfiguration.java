package com.projectmodule.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
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

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Scoped to controllers in this module, leaving actuator and the OpenAPI endpoints
        // on their conventional paths.
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage(BASE_PACKAGE));
    }
}
