package com.projectmodule.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.projectmodule.security.entitlement.EntitlementClient;
import com.projectmodule.security.entitlement.EntitlementFilter;
import com.projectmodule.security.entitlement.EntitlementService;
import com.projectmodule.security.identity.FederatedIdentityStore;
import com.projectmodule.security.oidc.AuthorizationRequestResolver;
import com.projectmodule.security.oidc.IdTokenDecoderFactory;
import com.projectmodule.security.oidc.PendingFlowRepository;
import com.projectmodule.security.oidc.ProviderHttp;
import com.projectmodule.security.oidc.Workeasy360OidcUserService;
import com.projectmodule.security.oidc.Workeasy360Provider;
import com.projectmodule.security.web.CsrfSupport;
import com.projectmodule.security.web.SignInEntryFilter;
import com.projectmodule.security.web.SignInHandlers;
import com.projectmodule.security.web.SignedOutMarker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import com.projectmodule.security.web.SameOriginWritesFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.util.Map;

/**
 * The security chain: who may reach what, how people sign in, and what happens to the session.
 *
 * <ul>
 *   <li>{@code /api/v1/**} (and everything else under {@code /api} except the sign-in endpoints) requires an
 *       authenticated session; the answer to anyone else is a 401 problem document. The static app, the
 *       sign-in endpoints and the actuator health probe are public.
 *   <li>Sign-in is Spring's OAuth2/OIDC login, customised to the Workeasy360 contract (see the classes in
 *       {@code security.oidc}). Nothing carries over from the pre-sign-in session
 *       ({@code sessionFixation().newSession()}), which also ends the session of whoever was signed in
 *       in that browser before.
 *   <li>CSRF is on, with the cookie-and-header pattern for the single-page app. The OAuth redirects are
 *       GETs, not writes.
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(Workeasy360Properties.class)
public class SecurityConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public static Workeasy360ConfigurationCheck workeasy360ConfigurationCheck(
            Workeasy360Properties properties, Environment environment) {
        return new Workeasy360ConfigurationCheck(properties, environment);
    }

    @Bean
    public ProviderHttp providerHttp(Workeasy360Properties properties, ObjectMapper objectMapper) {
        return new ProviderHttp(properties.httpTimeout(), objectMapper);
    }

    /** Also the {@code ClientRegistrationRepository}: the registration is discovered lazily. */
    @Bean
    public Workeasy360Provider workeasy360Provider(Workeasy360Properties properties, ProviderHttp http,
                                                   Clock clock) {
        return new Workeasy360Provider(properties, http, clock);
    }

    @Bean
    public ProblemResponses problemResponses(ObjectMapper objectMapper) {
        return new ProblemResponses(objectMapper);
    }

    /** Spring's OIDC provider finds this bean by type. */
    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(Workeasy360Properties properties,
                                                                       ProviderHttp http) {
        return new IdTokenDecoderFactory(properties.productCode(), http);
    }

    @Bean
    public EntitlementService entitlementService(Workeasy360Provider provider, Clock clock,
            @Value("${server.servlet.session.timeout:8h}") java.time.Duration sessionIdleTimeout) {
        return new EntitlementService(new EntitlementClient(provider), clock, sessionIdleTimeout);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Workeasy360Properties properties,
            Workeasy360Provider provider,
            FederatedIdentityStore identities,
            EntitlementService entitlements,
            ProblemResponses problems,
            Clock clock,
            ProviderHttp providerHttp,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) throws Exception {

        SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
        // Left by both sign-outs, cleared by a sign-in: keeps the app from signing a person straight back in.
        SignedOutMarker signedOut = new SignedOutMarker(secureCookies);

        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // Over https the cookie carries the __Host- prefix: the browser then refuses one set by a sibling
        // subdomain (cookie tossing) or with a Domain or a path other than /. Over plain http (the local
        // profile) the prefix is not allowed, so the plain name is used; the app reads either.
        csrfTokens.setCookieName(secureCookies ? "__Host-XSRF-TOKEN" : "XSRF-TOKEN");
        csrfTokens.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Lax").path("/"));

        AuthenticationEntryPoint unauthenticated = (request, response, exception) ->
                problems.unauthenticated(request, response);
        AccessDeniedHandler denied = (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                problems.write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Access denied",
                        "The security token for this request was missing or out of date. Reload the page "
                                + "and try again.",
                        Map.of("code", "csrf_invalid"));
            } else {
                problems.write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Access denied",
                        "You are not allowed to do that.", Map.of("code", "forbidden"));
            }
        };

        http
                .securityContext(context -> context.securityContextRepository(contexts))
                .sessionManagement(session -> session
                        // A new session at sign-in, with nothing carried over from the one that held the
                        // pending flow (or from a previous person's).
                        .sessionFixation(fixation -> fixation.newSession()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfSupport.SpaCsrfTokenRequestHandler()))
                // The success handler never uses a saved request, and the default cache would create a
                // session for every anonymous GET to a protected path.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        // Spring would mark every response no-store, including the hashed JavaScript
                        // bundles; the API and the auth endpoints get it from the filter below instead.
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(denied))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/v3/**", "/swagger-ui/**", "/swagger-ui.html").authenticated()
                        .requestMatchers("/api/auth/session").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // The packaged single-page app and its assets.
                        .anyRequest().permitAll())
                .oauth2Login(login -> login
                        .clientRegistrationRepository(provider)
                        .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                        .loginPage("/")
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new AuthorizationRequestResolver(provider))
                                .authorizationRequestRepository(new PendingFlowRepository(clock)))
                        .redirectionEndpoint(redirect -> redirect.baseUri("/api/auth/callback/*"))
                        .tokenEndpoint(token -> token.accessTokenResponseClient(tokenClient(providerHttp, provider)))
                        .userInfoEndpoint(userInfo -> userInfo
                                // The ID token carries everything; the userinfo endpoint is never called.
                                .oidcUserService(new Workeasy360OidcUserService(identities))
                                // A non-OIDC registration cannot sign anyone in here.
                                .userService(request -> {
                                    throw new OAuth2AuthenticationException(new OAuth2Error("refused"));
                                }))
                        .successHandler(SignInHandlers.success(contexts, signedOut))
                        .failureHandler(SignInHandlers.failure()))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(sessionCookieName)
                        .addLogoutHandler(signedOut.onLogout())
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterAfter(new CsrfSupport.CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(new NoStoreFilter(), CsrfFilter.class)
                .addFilterBefore(new SameOriginWritesFilter(problems), CsrfFilter.class)
                .addFilterBefore(new SignInEntryFilter(provider), OAuth2AuthorizationRequestRedirectFilter.class)
                .addFilterAfter(new EntitlementFilter(entitlements, problems), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * The token request: authorization code + PKCE, authenticated with a {@code private_key_jwt} signed by
     * this product's key (issuer = subject = client id, audience = the token endpoint, a unique {@code jti},
     * 60 seconds). Bounded by timeouts; anything unexpected becomes the sign-in error {@code unavailable}.
     */
    static OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenClient(
            ProviderHttp http, Workeasy360Provider provider) {
        RestClient restClient = RestClient.builder()
                // Deadline over the whole exchange (not just the headers), no redirects.
                .requestFactory(http.asRequestFactory())
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(restClient);
        client.addParametersConverter(new NimbusJwtClientAuthenticationParametersConverter<>(
                (ClientRegistration registration) -> clientKey(provider)));
        return request -> {
            try {
                return client.getTokenResponse(request);
            } catch (OAuth2AuthorizationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new OAuth2AuthorizationException(
                        new OAuth2Error(Workeasy360Provider.UNAVAILABLE, exception.getClass().getSimpleName(), null),
                        exception);
            }
        };
    }

    private static JWK clientKey(Workeasy360Provider provider) {
        try {
            return provider.clientKey();
        } catch (RuntimeException exception) {
            // Misconfigured credential: sign-in is unavailable, and the cause (never the key) is logged.
            throw new OAuth2AuthorizationException(
                    new OAuth2Error(Workeasy360Provider.UNAVAILABLE, "the client key cannot be used", null));
        }
    }

    /** No access token is kept: the ID token was spent at sign-in and nothing calls Workeasy360 on a person's behalf. */
    private static final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

        @Override
        @SuppressWarnings("unchecked")
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
                Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                HttpServletRequest request, HttpServletResponse response) {
            // Deliberately nothing.
        }

        @Override
        public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                HttpServletRequest request, HttpServletResponse response) {
            // Nothing was kept.
        }
    }

    /** API and authentication responses are never cached by the browser or a proxy. */
    private static final class NoStoreFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
            if (path.startsWith("/api/")) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
            }
            filterChain.doFilter(request, response);
        }
    }
}
