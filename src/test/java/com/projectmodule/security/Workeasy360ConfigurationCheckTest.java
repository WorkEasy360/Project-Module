package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.security.Workeasy360ConfigurationCheck.Analyzer;
import com.projectmodule.security.Workeasy360ConfigurationCheck.Workeasy360ConfigurationException;
import com.projectmodule.security.support.TestClientKeys;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("Production refuses to start without Workeasy360 sign-in configured")
class Workeasy360ConfigurationCheckTest {

    private static Workeasy360Properties properties(String issuer, String clientId, String redirect, String keyId,
                                                    String keyFile, String key) {
        return new Workeasy360Properties(issuer, clientId, redirect, keyId, keyFile, key, "easy_project", null,
                Duration.ofHours(1), Duration.ofSeconds(10));
    }

    private static Workeasy360Properties complete() {
        return properties("https://app.workeasy360.com", "client", "https://ops.workeasy360.com/api/auth/callback/workeasy360",
                "kid", TestClientKeys.RSA_FILE.toString(), null);
    }

    private static void check(Workeasy360Properties properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        new Workeasy360ConfigurationCheck(properties, environment).afterPropertiesSet();
    }

    @Test
    @DisplayName("a complete configuration starts")
    void completeStarts() {
        assertThatCode(() -> check(complete())).doesNotThrowAnyException();
        assertThatCode(() -> check(complete(), "production")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("each missing setting stops a non-local start and is named")
    void eachMissingSetting() {
        String redirect = "https://ops.workeasy360.com/api/auth/callback/workeasy360";
        String file = TestClientKeys.RSA_FILE.toString();
        Object[][] cases = {
                {properties(null, "c", redirect, "k", file, null), "WORKEASY360_ISSUER"},
                {properties(" ", "c", redirect, "k", file, null), "WORKEASY360_ISSUER"},
                {properties("https://i", null, redirect, "k", file, null), "WORKEASY360_CLIENT_ID"},
                {properties("https://i", "c", null, "k", file, null), "WORKEASY360_REDIRECT_URI"},
                {properties("https://i", "c", redirect, null, file, null), "WORKEASY360_KEY_ID"},
                {properties("https://i", "c", redirect, "k", null, null), "WORKEASY360_PRIVATE_KEY_FILE"},
                {properties("https://i", "c", redirect, "k", "", " "), "WORKEASY360_PRIVATE_KEY_FILE"},
        };
        for (Object[] testCase : cases) {
            Workeasy360Properties properties = (Workeasy360Properties) testCase[0];
            assertThatThrownBy(() -> check(properties, "production"))
                    .isInstanceOf(Workeasy360ConfigurationException.class)
                    .hasMessageContaining((String) testCase[1]);
            assertThatThrownBy(() -> check(properties)).as("no profile at all is not local")
                    .isInstanceOf(Workeasy360ConfigurationException.class);
        }
    }

    @Test
    @DisplayName("nothing at all: every setting is listed")
    void everythingMissing() {
        assertThatThrownBy(() -> check(properties(null, null, null, null, null, null)))
                .isInstanceOf(Workeasy360ConfigurationException.class)
                .hasMessageContaining("WORKEASY360_ISSUER").hasMessageContaining("WORKEASY360_CLIENT_ID")
                .hasMessageContaining("WORKEASY360_REDIRECT_URI").hasMessageContaining("WORKEASY360_KEY_ID")
                .hasMessageContaining("WORKEASY360_PRIVATE_KEY_FILE");
    }

    @Test
    @DisplayName("the local and test profiles may start without it (sign-in then answers 'unavailable')")
    void localAndTestProfilesMayStartWithoutIt() {
        Workeasy360Properties nothing = properties(null, null, null, null, null, null);

        assertThatCode(() -> check(nothing, "local")).doesNotThrowAnyException();
        assertThatCode(() -> check(nothing, "test")).doesNotThrowAnyException();
        assertThatCode(() -> check(nothing, "production", "local")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unreadable or unusable key stops the start, without printing it")
    void badKey() {
        String redirect = "https://ops.workeasy360.com/api/auth/callback/workeasy360";
        assertThatThrownBy(() -> check(properties("https://i", "c", redirect, "k", "/no/such/key.pem", null)))
                .isInstanceOf(Workeasy360ConfigurationException.class);
        assertThatThrownBy(() -> check(properties("https://i", "c", redirect, "k", null, "not a key")))
                .isInstanceOf(Workeasy360ConfigurationException.class).hasMessageNotContaining("not a key");
    }

    @Test
    @DisplayName("production issuer and redirect URI must be https")
    void httpsOnly() {
        assertThatThrownBy(() -> check(properties("http://app.workeasy360.com", "c",
                "https://ops.workeasy360.com/cb", "k", TestClientKeys.RSA_FILE.toString(), null)))
                .isInstanceOf(Workeasy360ConfigurationException.class);
        assertThatThrownBy(() -> check(properties("https://app.workeasy360.com", "c",
                "http://ops.workeasy360.com/cb", "k", TestClientKeys.RSA_FILE.toString(), null)))
                .isInstanceOf(Workeasy360ConfigurationException.class);
    }

    @Test
    @DisplayName("the failure analyzer names the settings to provide and never prints a value")
    void analyzer() {
        Workeasy360ConfigurationException failure = new Workeasy360ConfigurationException(
                "Workeasy360 sign-in is not configured; missing: WORKEASY360_ISSUER", java.util.List.of());

        FailureAnalysis analysis = new Analyzer().analyze(new IllegalStateException("startup", failure));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("missing: WORKEASY360_ISSUER");
        assertThat(analysis.getAction()).contains("WORKEASY360_ISSUER", "WORKEASY360_CLIENT_ID",
                "WORKEASY360_REDIRECT_URI", "WORKEASY360_KEY_ID", "WORKEASY360_PRIVATE_KEY_FILE",
                "SPRING_PROFILES_ACTIVE=local");
        assertThat(new Analyzer().analyze(new IllegalStateException("something else"))).isNull();
    }

    @Test
    @DisplayName("sign-out of Workeasy360 is offered only with a post-logout URI")
    void signOutOffered() {
        assertThat(complete().signOutOfWorkeasy360Offered()).isFalse();
        Workeasy360Properties with = new Workeasy360Properties("https://i", "c", "https://r", "k", "f", null,
                "easy_project", "https://ops.workeasy360.com/", Duration.ofHours(1), Duration.ofSeconds(10));
        assertThat(with.signOutOfWorkeasy360Offered()).isTrue();
    }

    @Configuration
    @EnableConfigurationProperties(Workeasy360Properties.class)
    static class CheckConfiguration {

        @Bean
        Workeasy360ConfigurationCheck check(Workeasy360Properties properties, Environment environment) {
            return new Workeasy360ConfigurationCheck(properties, environment);
        }
    }

    @Test
    @DisplayName("in a real application context: no settings and no local profile -> the context does not start")
    void contextRefusesToStart() {
        new ApplicationContextRunner().withUserConfiguration(CheckConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootOf(context.getStartupFailure())).isInstanceOf(Workeasy360ConfigurationException.class);
                });
        new ApplicationContextRunner().withUserConfiguration(CheckConfiguration.class)
                .withPropertyValues("workeasy360.issuer=https://app.workeasy360.com", "workeasy360.client-id=c")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("in a real application context: the environment names (WORKEASY360_*) bind, and a complete set starts")
    void contextStartsWhenConfigured() {
        new ApplicationContextRunner().withUserConfiguration(CheckConfiguration.class)
                .withPropertyValues(
                        "workeasy360.issuer=https://app.workeasy360.com",
                        "workeasy360.client-id=c",
                        "workeasy360.redirect-uri=https://ops.workeasy360.com/api/auth/callback/workeasy360",
                        "workeasy360.key-id=k",
                        "workeasy360.private-key-file=" + TestClientKeys.RSA_FILE.toString().replace("\\", "/"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Workeasy360Properties.class).productCode()).isEqualTo("easy_project");
                });
        new ApplicationContextRunner().withUserConfiguration(CheckConfiguration.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static Throwable rootOf(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
