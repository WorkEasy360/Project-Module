package com.projectmodule.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@DisplayName("Pending sign-ins: server-side, single use, ten minutes")
class PendingFlowRepositoryTest {

    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-12T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final PendingFlowRepository repository = new PendingFlowRepository(clock);
    private final MockHttpSession session = new MockHttpSession();

    private OAuth2AuthorizationRequest flow(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://idp.example/authorize")
                .clientId("client")
                .redirectUri("https://app.example/callback")
                .state(state)
                .build();
    }

    private MockHttpServletRequest browser(String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        if (state != null) {
            request.setParameter("state", state);
        }
        return request;
    }

    private void save(String state) {
        repository.saveAuthorizationRequest(flow(state), browser(null), new MockHttpServletResponse());
    }

    @Test
    @DisplayName("a flow is consumed by the first callback that names it")
    void singleUse() {
        save("s1");

        MockHttpServletRequest callback = browser("s1");
        assertThat(repository.removeAuthorizationRequest(callback, new MockHttpServletResponse())).isNotNull();
        assertThat(callback.getAttribute(PendingFlowRepository.CONSUMED_ATTRIBUTE)).isNotNull();
        assertThat(repository.removeAuthorizationRequest(browser("s1"), new MockHttpServletResponse())).isNull();
    }

    @Test
    @DisplayName("a flow older than ten minutes is consumed and refused")
    void tenMinutes() {
        save("s1");
        clock.now = clock.now.plus(Duration.ofMinutes(9)).plusSeconds(59);
        assertThat(repository.loadAuthorizationRequest(browser("s1"))).isNotNull();

        clock.now = clock.now.plusSeconds(2);

        assertThat(repository.loadAuthorizationRequest(browser("s1"))).isNull();
        MockHttpServletRequest late = browser("s1");
        assertThat(repository.removeAuthorizationRequest(late, new MockHttpServletResponse())).isNull();
        assertThat(late.getAttribute(PendingFlowRepository.CONSUMED_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("a wrong, missing, empty or oversized state matches nothing and consumes nothing")
    void wrongState() {
        save("s1");

        assertThat(repository.removeAuthorizationRequest(browser("s2"), new MockHttpServletResponse())).isNull();
        assertThat(repository.removeAuthorizationRequest(browser(null), new MockHttpServletResponse())).isNull();
        assertThat(repository.removeAuthorizationRequest(browser(""), new MockHttpServletResponse())).isNull();
        assertThat(repository.removeAuthorizationRequest(browser("s".repeat(600)), new MockHttpServletResponse()))
                .isNull();
        assertThat(repository.loadAuthorizationRequest(browser("s1"))).isNotNull();
    }

    @Test
    @DisplayName("two tabs: both flows are kept; only five per browser")
    void severalFlows() {
        save("tab-1");
        save("tab-2");
        assertThat(repository.removeAuthorizationRequest(browser("tab-1"), new MockHttpServletResponse())).isNotNull();
        assertThat(repository.removeAuthorizationRequest(browser("tab-2"), new MockHttpServletResponse())).isNotNull();

        for (int i = 0; i < 8; i++) {
            save("flow-" + i);
        }
        assertThat(repository.loadAuthorizationRequest(browser("flow-0"))).as("oldest dropped").isNull();
        assertThat(repository.loadAuthorizationRequest(browser("flow-3"))).as("oldest kept").isNotNull();
        assertThat(repository.loadAuthorizationRequest(browser("flow-7"))).isNotNull();
    }

    @Test
    @DisplayName("flows live in the browser's session: another session finds nothing")
    void boundToTheBrowser() {
        save("s1");
        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setSession(new MockHttpSession());
        other.setParameter("state", "s1");

        assertThat(repository.removeAuthorizationRequest(other, new MockHttpServletResponse())).isNull();
        assertThat(repository.removeAuthorizationRequest(new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isNull();
    }
}
