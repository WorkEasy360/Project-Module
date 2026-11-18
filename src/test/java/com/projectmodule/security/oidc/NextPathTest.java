package com.projectmodule.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("next: only a same-site route survives")
class NextPathTest {

    @ParameterizedTest(name = "{0} is kept")
    @ValueSource(strings = {"/", "/projects", "/projects/9b2f", "/projects/9b2f/tasks?status=OPEN", "/dashboard",
            "/projects/123/tasks/456"})
    void keepsSameSiteRoutes(String route) {
        assertThat(NextPath.sanitize(route)).isEqualTo(route);
    }

    @ParameterizedTest(name = "[{0}] falls back to the home page")
    @NullAndEmptySource
    @ValueSource(strings = {
            "//evil.example", "///evil.example", "/\\evil.example", "/\\\\evil.example", "\\evil.example",
            "https://evil.example", "http://evil.example/x", "javascript:alert(1)", "evil.example", "projects",
            // browsers drop tabs and newlines inside URLs: "/<tab>/evil" is "//evil"
            "/\t/evil.example", "/\n/evil.example", "/\r/evil.example", "/ /evil.example", "/projects /1",
            "/a\u0000b", "/a\u007fb", "/a b", "/a b",
            // encoded forms of the same things
            "/%2f/evil.example", "/%2F/evil.example", "/%5cevil.example", "/%5Cevil.example", "/%09/evil.example",
            "/%0a/evil.example", "/%0D/evil.example", "/%00", "/%7f",
            // authentication pages and the API are not somewhere to land
            "/sign-in", "/sign-in/", "/sign-in?sso_error=expired", "/SIGN-IN", "/api/v1/projects", "/api", "/api/auth/session",
            "/API/v1/projects",
            // a second fragment
            "/projects#x"})
    void refusesEverythingElse(String value) {
        assertThat(NextPath.sanitize(value)).isEqualTo("/");
    }

    @Test
    @DisplayName("a value over 2,048 characters falls back to the home page")
    void refusesLongValues() {
        assertThat(NextPath.sanitize("/" + "a".repeat(2047))).hasSize(2048);
        assertThat(NextPath.sanitize("/" + "a".repeat(2048))).isEqualTo("/");
    }

    @Test
    @DisplayName("the hash form a caller may already have written is reduced to the route")
    void acceptsTheHashForm() {
        assertThat(NextPath.sanitize("/#/projects/1")).isEqualTo("/projects/1");
        assertThat(NextPath.sanitize("/#//evil.example")).isEqualTo("/");
        assertThat(NextPath.sanitize("/#/sign-in")).isEqualTo("/");
    }

    @Test
    @DisplayName("the landing URL puts the route in the fragment, so the server never sees it again")
    void landingUrl() {
        assertThat(NextPath.landingUrl("/")).isEqualTo("/");
        assertThat(NextPath.landingUrl("/projects/1")).isEqualTo("/#/projects/1");
    }
}
