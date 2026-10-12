package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.FakeWorkeasy360.EntitlementReply;
import com.projectmodule.security.support.FakeWorkeasy360.IdTokenSpec;
import com.projectmodule.security.support.SsoFlowTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Every way a sign-in can be wrong, and the one thing they all share: it fails closed. The browser lands on
 * the sign-in page with a code from the fixed vocabulary, and no session exists afterwards.
 */
@DisplayName("Sign-in failures fail closed")
class SignInFailuresTest extends SsoFlowTestBase {

    private static final String ORG = "7d1d6a35-6f0f-4c06-bd0a-0d2f4f0d4a11";
    private static final String OTHER_ORG = "0e5b1f3a-9a4e-4d2c-8c11-5b7a1c2d3e4f";

    /** The sign-in page said {@code expected}, and the browser is not signed in. */
    private void assertFailedClosed(MvcResult result, String expected) throws Exception {
        assertThat(ssoError(result)).as("redirect to %s", location(result)).isEqualTo(expected);
        MockHttpSession session = sessionOf(result);
        if (session != null) {
            mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/dashboard").session(session == null ? new MockHttpSession() : session))
                .andExpect(status().isUnauthorized());
    }

    private static Arguments token(String name, String expected, Consumer<IdTokenSpec> change) {
        return Arguments.of(name, expected, change);
    }

    static Stream<Arguments> badTokens() {
        return Stream.of(
                token("wrong audience", "refused", s -> s.audience = "some-other-client"),
                token("audience that lists this client and another", "refused",
                        s -> s.audience = List.of(FakeWorkeasy360.CLIENT_ID, "some-other-client")),
                token("token minted for another product's client (cross-product)", "refused",
                        s -> s.audience = "easy-crm-client"),
                token("wrong issuer", "refused", s -> s.issuer = "https://evil.example"),
                token("issuer with a trailing slash", "refused", s -> s.issuer = s.issuer + "/"),
                token("wrong product code", "wrong_product", s -> s.productCode = "easy_crm"),
                token("no product code", "wrong_product", s -> s.omit.add("product_code")),
                token("wrong nonce", "refused", s -> s.nonce = "not-the-nonce-we-sent"),
                token("no nonce", "refused", s -> s.omit.add("nonce")),
                token("expired token", "refused", s -> {
                    s.issuedAt = Instant.now().minusSeconds(900);
                    s.expiresAt = Instant.now().minusSeconds(600);
                }),
                token("issued in the future", "refused", s -> {
                    s.issuedAt = Instant.now().plusSeconds(300);
                    s.expiresAt = Instant.now().plusSeconds(600);
                }),
                token("not yet valid", "refused", s -> s.notBefore = Instant.now().plusSeconds(300)),
                token("an access token presented as an ID token", "refused", s -> s.typ = "access_token"),
                token("no typ", "refused", s -> s.omit.add("typ")),
                token("no jti", "refused", s -> s.omit.add("jti")),
                token("no subject", "refused", s -> s.omit.add("sub")),
                token("no organization", "refused", s -> s.omit.add("organization_id")),
                token("organization that is not a UUID", "refused", s -> s.organizationId = "acme"),
                token("no expiry", "refused", s -> s.omit.add("exp")),
                token("no issued-at", "refused", s -> s.omit.add("iat")),
                token("unsigned (alg none)", "refused", s -> s.signing = "NONE"),
                token("HS256 keyed with the public key (algorithm confusion)", "refused",
                        s -> s.signing = "HS256_WITH_PUBLIC_KEY"),
                token("signed by a different key under the right kid", "refused", s -> s.signing = "WRONG_KEY"),
                token("a kid that is not in the key set", "refused", s -> s.signing = "UNKNOWN_KID"),
                token("no kid in the header at all", "refused", s -> s.signing = "NO_KID"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("badTokens")
    @DisplayName("a bad ID token is refused")
    void badIdToken(String name, String expected, Consumer<IdTokenSpec> change) throws Exception {
        MvcResult result = signInFresh(null, change);

        assertFailedClosed(result, expected);
    }

    @Test
    @DisplayName("a token a few seconds past its expiry is still inside the 30 s leeway")
    void leewayIsThirtySeconds() throws Exception {
        MvcResult result = signInFresh(null, s -> {
            s.issuedAt = Instant.now().minusSeconds(320);
            s.expiresAt = Instant.now().minusSeconds(20);
        });
        assertThat(ssoError(result)).isNull();

        MvcResult tooOld = signInFresh(null, s -> {
            s.issuedAt = Instant.now().minusSeconds(360);
            s.expiresAt = Instant.now().minusSeconds(40);
        });
        assertFailedClosed(tooOld, "refused");
    }

    @Test
    @DisplayName("an ES256-signed token is accepted as well as RS256")
    void ecSignedTokensAreAccepted() throws Exception {
        MvcResult result = signInFresh(null, s -> s.useEc = true);

        assertThat(ssoError(result)).isNull();
        assertThat(location(result)).isEqualTo("/");
    }

    // --- state, flow and code ---------------------------------------------------------------------

    @Test
    @DisplayName("a wrong state is refused as expired")
    void wrongState() throws Exception {
        Started started = start(null);
        String code = FAKE.issueCode(started.authorization(), null);

        MvcResult result = callback(started, code, "a-state-we-never-issued");

        assertFailedClosed(result, "expired");
        assertThat(FAKE.tokenRequests).as("no code was exchanged").isEmpty();
    }

    @Test
    @DisplayName("a callback with no state is refused as expired")
    void missingState() throws Exception {
        Started started = start(null);
        String code = FAKE.issueCode(started.authorization(), null);

        MvcResult result = mvc.perform(get("/api/auth/callback/workeasy360").param("code", code)
                .session(started.session())).andReturn();

        assertFailedClosed(result, "expired");
        assertThat(FAKE.tokenRequests).isEmpty();
    }

    @Test
    @DisplayName("a callback with neither code nor state is refused as expired")
    void emptyCallback() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/callback/workeasy360")).andReturn();

        assertFailedClosed(result, "expired");
    }

    @Test
    @DisplayName("a state that is valid but belongs to another browser's session is refused (login CSRF)")
    void stateFromAnotherBrowser() throws Exception {
        Started victimsFlow = start(null);
        Started attackersFlow = start(null);
        String attackersCode = FAKE.issueCode(attackersFlow.authorization(), null);

        // The attacker plants their own code and state in the victim's browser: the victim's session holds
        // a different pending flow, so the callback matches nothing.
        MvcResult result = callback(victimsFlow, attackersCode, attackersFlow.authorization().state());

        assertFailedClosed(result, "expired");
        assertThat(FAKE.tokenRequests).isEmpty();
    }

    @Test
    @DisplayName("the pending flow is single use: a replayed callback is expired")
    void callbackCannotBeReplayed() throws Exception {
        Started started = start(null);
        String code = FAKE.issueCode(started.authorization(), null);
        MvcResult first = callback(started, code, started.authorization().state());
        assertThat(ssoError(first)).isNull();

        // The same URL again, in the same (now replaced) browser session or in any other.
        MvcResult replay = callback(new Started(sessionOf(first), started.location(), started.authorization()),
                code, started.authorization().state());

        assertThat(ssoError(replay)).isEqualTo("expired");
        assertThat(FAKE.tokenRequests).hasSize(1);
    }

    @Test
    @DisplayName("a flow is consumed even when its sign-in fails")
    void failedFlowIsConsumed() throws Exception {
        Started started = start(null);
        String code = FAKE.issueCode(started.authorization(), spec -> spec.audience = "someone-else");
        assertThat(ssoError(callback(started, code, started.authorization().state()))).isEqualTo("refused");

        String second = FAKE.issueCode(started.authorization(), null);
        MvcResult retry = callback(started, second, started.authorization().state());

        assertThat(ssoError(retry)).isEqualTo("expired");
    }

    @Test
    @DisplayName("a PKCE verifier that does not match the challenge is refused by the provider -> refused")
    void pkceMismatch() throws Exception {
        Started started = start(null);
        FakeWorkeasy360.Authorization seen = started.authorization();
        // The provider remembers a different challenge than the verifier we hold.
        FakeWorkeasy360.Authorization other = new FakeWorkeasy360.Authorization(seen.state(), seen.nonce(),
                "x".repeat(43), seen.codeChallengeMethod(), seen.organization(), seen.clientId(),
                seen.redirectUri(), seen.scope(), seen.responseType(), seen.all());
        String code = FAKE.issueCode(other, null);

        MvcResult result = callback(started, code, seen.state());

        assertFailedClosed(result, "refused");
        assertThat(FAKE.tokenRequests).hasSize(1);
        assertThat(FAKE.tokenRequests.get(0)).containsKey("code_verifier");
    }

    @Test
    @DisplayName("an expired code (the token endpoint says invalid_grant) -> refused")
    void expiredCode() throws Exception {
        FAKE.tokenOverride = request -> new EntitlementReply(400, Map.of("error", "invalid_grant"));

        assertFailedClosed(signInFresh(null, null), "refused");
    }

    @Test
    @DisplayName("a code used twice: the second exchange is refused by the provider -> refused")
    void reusedCode() throws Exception {
        Started first = start(null);
        String code = FAKE.issueCode(first.authorization(), null);
        assertThat(ssoError(callback(first, code, first.authorization().state()))).isNull();

        // An attacker (or a retry) presents the already-spent code with a fresh flow of their own.
        Started second = start(null);
        FAKE.issueCode(second.authorization(), null);
        MvcResult replay = callback(second, code, second.authorization().state());

        assertFailedClosed(replay, "refused");
    }

    // --- organization -------------------------------------------------------------------------------

    @Test
    @DisplayName("the launcher asked for organization A and the token says B -> wrong_organization")
    void crossOrganizationLaunch() throws Exception {
        Started started = start("organization=" + ORG);

        MvcResult result = signIn(started, spec -> spec.organizationId = OTHER_ORG);

        assertFailedClosed(result, "wrong_organization");
        assertThat(identities.size()).as("no identity was provisioned for a refused sign-in").isZero();
    }

    @Test
    @DisplayName("with no organization asked for, the token's organization is the session's")
    void noOrganizationAskedFor() throws Exception {
        MvcResult result = signInFresh(null, spec -> spec.organizationId = OTHER_ORG);

        assertThat(ssoError(result)).isNull();
        assertThat(sessionOf(result)).isNotNull();
    }

    @Test
    @DisplayName("an organization that is not a UUID is not forwarded to the provider")
    void organizationIsForwardedOnlyWhenAUuid() throws Exception {
        assertThat(start("organization=acme").authorization().organization()).isNull();
        assertThat(start("organization=").authorization().organization()).isNull();
        assertThat(start("organization=" + ORG.toUpperCase()).authorization().organization()).isEqualTo(ORG);
        assertThat(start(null).authorization().organization()).isNull();
    }

    // --- provider errors -------------------------------------------------------------------------------

    static Stream<Arguments> providerErrors() {
        return Stream.of(
                Arguments.of("access_denied", "cancelled"),
                Arguments.of("login_required", "expired"),
                Arguments.of("organization_required", "expired"),
                Arguments.of("product_not_entitled", "not_entitled"),
                Arguments.of("product_no_seat", "no_seat"),
                Arguments.of("membership_inactive", "provider_membership_inactive"),
                Arguments.of("organization_inactive", "organization_inactive"),
                Arguments.of("access_policy_denied", "access_policy"),
                Arguments.of("integration_suspended", "unavailable"),
                Arguments.of("unauthorized_client", "unavailable"),
                Arguments.of("interaction_required", "expired"),
                Arguments.of("invalid_scope", "unavailable"),
                Arguments.of("server_error", "unavailable"),
                Arguments.of("temporarily_unavailable", "unavailable"),
                Arguments.of("something_new", "refused"),
                Arguments.of("<script>", "refused"));
    }

    @ParameterizedTest(name = "error={0} -> {1}")
    @MethodSource("providerErrors")
    @DisplayName("the provider's error values map exactly (a suspended organization, a removed user, an ended entitlement...)")
    void providerError(String error, String expected) throws Exception {
        Started started = start(null);

        MvcResult result = mvc.perform(get("/api/auth/callback/workeasy360")
                .param("error", error).param("state", started.authorization().state())
                .session(started.session())).andReturn();

        assertFailedClosed(result, expected);
        // ...and the flow it named is gone.
        String code = FAKE.issueCode(started.authorization(), null);
        assertThat(ssoError(callback(started, code, started.authorization().state()))).isEqualTo("expired");
    }

    @Test
    @DisplayName("a provider error with no state still lands on the sign-in page with a mapped code")
    void providerErrorWithoutState() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/callback/workeasy360").param("error", "access_denied"))
                .andReturn();

        assertFailedClosed(result, "cancelled");
    }

    // --- Workeasy360 down ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the token endpoint answering 503 -> unavailable")
    void tokenEndpointDown() throws Exception {
        FAKE.tokenOverride = request -> new EntitlementReply(503, Map.of("error", "temporarily_unavailable"));

        assertFailedClosed(signInFresh(null, null), "unavailable");
    }

    @Test
    @DisplayName("the token endpoint answering 500 with no JSON -> unavailable")
    void tokenEndpointBroken() throws Exception {
        FAKE.tokenOverride = request -> new EntitlementReply(500, Map.of());

        assertFailedClosed(signInFresh(null, null), "unavailable");
    }

    @Test
    @DisplayName("discovery down: starting a sign-in lands on the sign-in page with 'unavailable' (and the app booted)")
    void discoveryDownAtStart() throws Exception {
        FAKE.discoveryDown = true;

        MvcResult result = mvc.perform(get("/api/auth/workeasy360/start")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(result)).isEqualTo("/#/sign-in?sso_error=unavailable");
    }

    @Test
    @DisplayName("discovery that names another issuer -> unavailable")
    void discoveryNamesAnotherIssuer() throws Exception {
        FAKE.discoveryIssuerOverride = "https://evil.example";

        MvcResult result = mvc.perform(get("/api/auth/workeasy360/start")).andReturn();

        assertThat(location(result)).isEqualTo("/#/sign-in?sso_error=unavailable");
    }

    @Test
    @DisplayName("discovery recovering: the next sign-in works without a restart")
    void discoveryRecovers() throws Exception {
        FAKE.discoveryDown = true;
        mvc.perform(get("/api/auth/workeasy360/start")).andReturn();
        FAKE.discoveryDown = false;
        // The failed attempt is not repeated for a few seconds.
        provider.forgetDiscovery();

        assertThat(start(null).authorization().state()).isNotBlank();
    }

    @Test
    @DisplayName("Workeasy360 vanishing between start and callback -> unavailable")
    void discoveryDownAtCallback() throws Exception {
        Started started = start(null);
        String code = FAKE.issueCode(started.authorization(), null);
        FAKE.discoveryDown = true;
        provider.forgetDiscovery();

        MvcResult result = callback(started, code, started.authorization().state());

        assertFailedClosed(result, "unavailable");
    }

    @Test
    @DisplayName("a key set that cannot be fetched for an unknown kid -> unavailable (not 'refused')")
    void keySetDown() throws Exception {
        FAKE.jwksDown = true;

        MvcResult result = signInFresh(null, spec -> spec.signing = "UNKNOWN_KID");

        assertFailedClosed(result, "unavailable");
    }

    @Test
    @DisplayName("no failure above ever created a session or a user")
    void nothingWasProvisioned() throws Exception {
        signInFresh(null, spec -> spec.audience = "x");
        signInFresh(null, spec -> spec.productCode = "x");

        assertThat(identities.size()).isZero();
    }
}
