package com.projectmodule.config;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.context.RequestContextFactory;
import com.projectmodule.security.Workeasy360Principal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;

/**
 * Wires the caller-identity boundary.
 *
 * <p>The {@link RequestContext} of a request is derived from the authenticated session principal and from
 * nothing else. There are no identity headers, no fallback and no "development mode" that reads them.
 */
@Configuration
public class RequestContextConfiguration {

    @Bean
    public RequestContextFactory requestContextFactory() {
        return new RequestContextFactory();
    }

    /** Registered early so every response, including the ones security produces, carries the correlation id. */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Exposes the current request's context for injection.
     *
     * <p>Injected as a scoped proxy so singletons can depend on it directly and still resolve the correct
     * per-request instance. It is first used inside a controller, i.e. after the security filter chain has
     * authenticated the request.
     */
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.INTERFACES)
    public RequestContext requestContext(RequestContextFactory factory, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = null;
        UUID organizationId = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Workeasy360Principal principal) {
            userId = principal.userId();
            organizationId = principal.organizationId();
        }
        return factory.create(userId, organizationId, CorrelationIdFilter.of(request));
    }
}
