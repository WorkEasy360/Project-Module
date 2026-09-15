package com.projectmodule.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.context.RequestContextFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.web.context.WebApplicationContext;

/**
 * Wires the caller-identity boundary.
 *
 * <p>Note what is not here: no credential checking, no token parsing, no session handling.
 * This module consumes an identity that has already been authenticated elsewhere.
 */
@Configuration
public class RequestContextConfiguration {

    @Bean
    public RequestContextFactory requestContextFactory() {
        return new RequestContextFactory();
    }

    /** Registered early so the context is available to everything downstream. */
    @Bean
    public FilterRegistrationBean<RequestContextFilter> requestContextFilterRegistration(
            RequestContextFactory factory, ObjectMapper objectMapper) {

        FilterRegistrationBean<RequestContextFilter> registration =
                new FilterRegistrationBean<>(new RequestContextFilter(factory, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Exposes the current request's context for injection.
     *
     * <p>Injected as a scoped proxy so singletons can depend on it directly and still resolve
     * the correct per-request instance.
     */
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.INTERFACES)
    public RequestContext requestContext(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestContextFilter.ATTRIBUTE);
        return attribute instanceof RequestContext context
                ? context
                : ImmutableRequestContext.anonymous();
    }
}
