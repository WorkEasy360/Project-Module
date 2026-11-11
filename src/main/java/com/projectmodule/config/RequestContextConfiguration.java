package com.projectmodule.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.common.context.BearerTokenAuthenticator;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.context.RequestContextFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.web.context.WebApplicationContext;

/**
 * Wires the caller-identity boundary. Credentials themselves are handled by the {@code account}
 * package, behind {@link BearerTokenAuthenticator}.
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
            RequestContextFactory factory,
            ObjectMapper objectMapper,
            BearerTokenAuthenticator authenticator,
            @Value("${projectmodule.auth.trust-identity-headers:false}") boolean trustIdentityHeaders) {

        FilterRegistrationBean<RequestContextFilter> registration = new FilterRegistrationBean<>(
                new RequestContextFilter(factory, objectMapper, authenticator, trustIdentityHeaders));
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
