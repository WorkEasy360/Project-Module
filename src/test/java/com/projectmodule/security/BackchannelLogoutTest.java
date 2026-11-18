package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.nimbusds.jose.jwk.RSAKey;
import com.projectmodule.security.backchannel.SignInSessions;
import com.projectmodule.security.oidc.ProviderKeySet;
import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.FakeWorkeasy360.LogoutTokenSpec;
import com.projectmodule.security.support.SsoFlowTestBase;
import com.projectmodule.security.support.SsoTestApplication;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * OpenID Connect Back-Channel Logout 1.0, the receiver: Workeasy360 POSTs a logout token and the Easy Project
 * sessions it names end. Driven through the real security chain against {@link FakeWorkeasy360}, which signs
 * the logout tokens exactly as the platform does (ADR-069) unless a test bends them.
 */
@DisplayName("Back-channel logout: Workeasy360 ends the sessions a sign-in opened")
class BackchannelLogoutTest extends SsoFlowTestBase {

    private static final String PATH = "/api/auth/workeasy360/backchannel-logout";

    @Autowired
    private ProviderKeySet keySet;

    @Autowired
    private SignInSessions signIns;

    @Autowired
    private SsoTestApplication.InMemoryReceivedLogoutTokens received;

    @BeforeEach
    void freshState() {
        keySet.forget();
        received.clear();
    }

    // --- the endpoint does what the token says ---------------------------------------------------------

    @Test
    @DisplayName("a token naming a sid ends the sessions that sign-in opened, and only those")
    void sidEndsThatSignInOnly() throws Exception {
        String person = uuid();
        String sid = uuid();
        MockHttpSession laptop = signedIn(person, sid);
        MockHttpSession phone = signedIn(person, uuid());
        MockHttpSession colleague = signedIn(uuid(), uuid());

        MvcResult result = deliver(token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        }));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeaders("Cache-Control")).containsExactly("no-store");
        assertThat(laptop.isInvalid()).as("ended server-side").isTrue();
        assertThat(signedInStill(laptop)).isFalse();
        assertThat(signedInStill(phone)).as("the same person's other sign-in").isTrue();
        assertThat(signedInStill(colleague)).isTrue();
    }

    @Test
    @DisplayName("a token with only sub ends every session of that person, whichever sign-in opened it")
    void subOnlyEndsEverySessionOfThePerson() throws Exception {
        String person = uuid();
        MockHttpSession first = signedIn(person, uuid());
        MockHttpSession second = signedIn(person, uuid());
        // A sign-in whose ID token carried no sid (before the back-channel URI was registered).
        MockHttpSession withoutSid = signedIn(person, null);
        MockHttpSession colleague = signedIn(uuid(), uuid());

        MvcResult result = deliver(token(spec -> spec.subject = person));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(signedInStill(first)).isFalse();
        assertThat(signedInStill(second)).isFalse();
        assertThat(signedInStill(withoutSid)).isFalse();
        assertThat(signedInStill(colleague)).isTrue();
    }

    @Test
    @DisplayName("a token with only a sid ends that sign-in's sessions")
    void sidWithoutSub() throws Exception {
        String person = uuid();
        String sid = uuid();
        MockHttpSession named = signedIn(person, sid);
        MockHttpSession other = signedIn(person, uuid());

        assertThat(deliver(token(spec -> spec.sid = sid)).getResponse().getStatus()).isEqualTo(200);

        assertThat(signedInStill(named)).isFalse();
        assertThat(signedInStill(other)).isTrue();
    }

    @Test
    @DisplayName("a token naming a sid with somebody else's sub ends nothing")
    void sidOfAnotherPerson() throws Exception {
        String sid = uuid();
        MockHttpSession session = signedIn(uuid(), sid);

        MvcResult result = deliver(token(spec -> {
            spec.subject = uuid();
            spec.sid = sid;
        }));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(signedInStill(session)).isTrue();
    }

    @Test
    @DisplayName("delivery is at least once: a token for sessions that have already ended is a 200 that ends nothing")
    void endingAnEndedSessionIsANoOp() throws Exception {
        String person = uuid();
        String sid = uuid();
        MockHttpSession session = signedIn(person, sid);

        assertThat(deliver(token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        })).getResponse().getStatus()).isEqualTo(200);
        // Workeasy360 mints a fresh token (a new jti) for every attempt.
        assertThat(deliver(token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        })).getResponse().getStatus()).isEqualTo(200);
        assertThat(signedInStill(session)).isFalse();

        // A session the person ended themselves, and a person Easy Project has never seen.
        MockHttpSession signedOut = signedIn(person, uuid());
        signedOut.invalidate();
        assertThat(deliver(token(spec -> spec.subject = person)).getResponse().getStatus()).isEqualTo(200);
        assertThat(deliver(token(spec -> spec.subject = uuid())).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ES256 tokens verify, and a key set that publishes each key's alg is honoured")
    void es256AndPublishedAlgorithms() throws Exception {
        FAKE.publishKeyAlgorithms = true;
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());

        MvcResult result = deliver(token(spec -> {
            spec.subject = person;
            spec.useEc = true;
        }));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(signedInStill(session)).isFalse();
    }

    // --- a token that does not verify ends nothing ------------------------------------------------------

    static Stream<Arguments> brokenTokens() {
        Instant now = Instant.now();
        String event = LogoutTokenSpec.EVENT;
        return Stream.of(
                broken("iss is another issuer", spec -> spec.issuer = "https://evil.example"),
                broken("iss differs by a trailing slash", spec -> spec.issuer = FAKE.issuer() + "/"),
                broken("aud is another client", spec -> spec.audience = "another-client"),
                broken("aud is missing", spec -> spec.omit.add("aud")),
                broken("several audiences and no azp",
                        spec -> spec.audience = List.of(FakeWorkeasy360.CLIENT_ID, "another-client")),
                broken("azp names another client", spec -> spec.extra.put("azp", "another-client")),
                broken("signed by another key under the right kid", spec -> spec.signing = "WRONG_KEY"),
                broken("HS256 with the public key as the secret", spec -> spec.signing = "HS256_WITH_PUBLIC_KEY"),
                broken("unsigned (alg none)", spec -> spec.signing = "NONE"),
                broken("no kid", spec -> spec.signing = "NO_KID"),
                broken("expired", spec -> {
                    spec.issuedAt = now.minusSeconds(300);
                    spec.expiresAt = now.minusSeconds(120);
                }),
                broken("exp missing", spec -> spec.omit.add("exp")),
                broken("iat missing", spec -> spec.omit.add("iat")),
                broken("issued in the future", spec -> {
                    spec.issuedAt = now.plusSeconds(300);
                    spec.expiresAt = now.plusSeconds(420);
                }),
                broken("older than ten minutes although exp allows it", spec -> {
                    spec.issuedAt = now.minusSeconds(3600);
                    spec.expiresAt = now.plusSeconds(60);
                }),
                broken("nbf in the future", spec -> spec.extra.put("nbf", Date.from(now.plusSeconds(300)))),
                broken("nonce present", spec -> spec.extra.put("nonce", "n")),
                broken("events missing", spec -> spec.omit.add("events")),
                broken("events is not an object", spec -> spec.events = event),
                broken("events lacks the back-channel-logout event",
                        spec -> spec.events = Map.of("http://example.com/other-event", Map.of())),
                broken("the back-channel-logout event is not an object", spec -> spec.events = Map.of(event, "yes")),
                broken("jti missing", spec -> spec.omit.add("jti")),
                broken("neither sub nor sid", spec -> {
                    spec.omit.add("sub");
                    spec.omit.add("sid");
                }),
                broken("sub is blank", spec -> spec.subject = " "),
                broken("sid is longer than 255 characters", spec -> spec.sid = "s".repeat(256)),
                broken("typ header missing", spec -> spec.headerType = null),
                broken("typ header is JWT", spec -> spec.headerType = "JWT"),
                broken("typ claim is id_token", spec -> spec.typ = "id_token"));
    }

    private static Arguments broken(String why, Consumer<LogoutTokenSpec> breakIt) {
        return Arguments.of(why, breakIt);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenTokens")
    @DisplayName("a token failing any check is a 400 and ends nothing")
    void brokenTokenIsRefused(String why, Consumer<LogoutTokenSpec> breakIt) throws Exception {
        String person = uuid();
        String sid = uuid();
        MockHttpSession session = signedIn(person, sid);

        MvcResult result = deliver(token(spec -> {
            spec.subject = person;
            spec.sid = sid;
            breakIt.accept(spec);
        }));

        assertThat(result.getResponse().getStatus()).as(why).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"invalid_request\"");
        assertThat(result.getResponse().getHeaders("Cache-Control")).containsExactly("no-store");
        assertThat(signedInStill(session)).as(why).isTrue();
    }

    @Test
    @DisplayName("an ID token is not a logout token, even one for this client and this sign-in")
    void idTokenIsNotALogoutToken() throws Exception {
        String person = uuid();
        String sid = uuid();
        MockHttpSession session = signedIn(person, sid);
        FakeWorkeasy360.IdTokenSpec idToken = new FakeWorkeasy360.IdTokenSpec();
        idToken.issuer = FAKE.issuer();
        idToken.audience = FakeWorkeasy360.CLIENT_ID;
        idToken.subject = person;
        idToken.organizationId = uuid();
        idToken.nonce = "n";
        idToken.extra.put("sid", sid);

        assertThat(deliver(FAKE.mint(idToken)).getResponse().getStatus()).isEqualTo(400);

        // Even given the event and stripped of its nonce, its header does not say logout+jwt.
        idToken.nonce = null;
        idToken.extra.put("events", Map.of(LogoutTokenSpec.EVENT, Map.of()));
        assertThat(deliver(FAKE.mint(idToken)).getResponse().getStatus()).isEqualTo(400);
        assertThat(signedInStill(session)).isTrue();
    }

    @Test
    @DisplayName("no logout_token, or something that is not a JWT, is a 400")
    void missingOrGarbage() throws Exception {
        assertThat(mvc.perform(post(PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(deliver("").getResponse().getStatus()).isEqualTo(400);
        assertThat(deliver("not-a-jwt").getResponse().getStatus()).isEqualTo(400);
        assertThat(deliver("a.b.c").getResponse().getStatus()).isEqualTo(400);
        assertThat(deliver("x".repeat(20_000)).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("a jti is acted on once: the same token delivered again later gets the same 200 and ends nothing")
    void replayIsRefused() throws Exception {
        String person = uuid();
        MockHttpSession first = signedIn(person, uuid());
        String logoutToken = token(spec -> spec.subject = person);

        assertThat(deliver(logoutToken).getResponse().getStatus()).isEqualTo(200);
        assertThat(signedInStill(first)).isFalse();

        MockHttpSession later = signedIn(person, uuid());
        MvcResult replay = deliver(logoutToken);

        // A duplicate (or a retry whose first answer was lost) is answered with the success the first delivery got;
        // being acted on once is what protects the person's later sessions.
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getContentAsString()).isEmpty();
        assertThat(signedInStill(later)).as("the person's new session survives the replay").isTrue();
    }

    // --- keys ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a token signed with a newly published key: the key set is read again, once, and the token accepted")
    void newKeyIsFollowed() throws Exception {
        assertThat(deliver(token(spec -> spec.subject = uuid())).getResponse().getStatus())
                .as("the key set is now cached").isEqualTo(200);
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());
        RSAKey rotated = FAKE.rotateIn();
        int before = FAKE.jwksCalls;

        MvcResult result = deliver(token(spec -> {
            spec.subject = person;
            spec.signWith = rotated;
        }));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(FAKE.jwksCalls - before).as("exactly one re-read").isEqualTo(1);
        assertThat(signedInStill(session)).isFalse();
    }

    @Test
    @DisplayName("a kid nobody publishes: one re-read, then 400; another within 30 seconds is not re-read (503, retried)")
    void unknownKid() throws Exception {
        assertThat(deliver(token(spec -> spec.subject = uuid())).getResponse().getStatus()).isEqualTo(200);
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());
        int before = FAKE.jwksCalls;

        MvcResult first = deliver(token(spec -> {
            spec.subject = person;
            spec.signing = "UNKNOWN_KID";
        }));

        assertThat(first.getResponse().getStatus()).isEqualTo(400);
        assertThat(FAKE.jwksCalls - before).as("one re-read of the key set").isEqualTo(1);

        MvcResult second = deliver(token(spec -> {
            spec.subject = person;
            spec.signing = "UNKNOWN_KID";
        }));

        assertThat(second.getResponse().getStatus()).as("cannot tell yet: Workeasy360 will retry").isEqualTo(503);
        assertThat(FAKE.jwksCalls - before).as("no amplification").isEqualTo(1);
        assertThat(signedInStill(session)).isTrue();
    }

    @Test
    @DisplayName("the key set cannot be read: 503, nothing ends; Workeasy360's retry then succeeds")
    void keySetUnavailable() throws Exception {
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());
        String logoutToken = token(spec -> spec.subject = person);
        FAKE.jwksDown = true;

        MvcResult down = deliver(logoutToken);

        assertThat(down.getResponse().getStatus()).isEqualTo(503);
        assertThat(down.getResponse().getContentAsString()).contains("\"temporarily_unavailable\"");
        assertThat(down.getResponse().getHeaders("Cache-Control")).containsExactly("no-store");
        assertThat(signedInStill(session)).isTrue();

        FAKE.jwksDown = false;
        assertThat(deliver(logoutToken).getResponse().getStatus()).isEqualTo(200);
        assertThat(signedInStill(session)).isFalse();
    }

    @Test
    @DisplayName("Workeasy360 cannot be discovered: 503 and nothing ends")
    void discoveryUnavailable() throws Exception {
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());
        String logoutToken = token(spec -> spec.subject = person);
        FAKE.discoveryDown = true;
        provider.forgetDiscovery();

        assertThat(deliver(logoutToken).getResponse().getStatus()).isEqualTo(503);
        FAKE.discoveryDown = false;
        provider.forgetDiscovery();
        assertThat(signedInStill(session)).isTrue();
    }

    // --- failures to record or to end: 503, nothing recorded, so the retry works -------------------------

    @Test
    @DisplayName("the jti cannot be recorded (database down): 503, nothing ends, and the retry is not a replay")
    void ledgerUnavailable() throws Exception {
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());
        String logoutToken = token(spec -> spec.subject = person);
        received.failNextWith(new CannotCreateTransactionException("database unavailable"));

        MvcResult failed = deliver(logoutToken);

        assertThat(failed.getResponse().getStatus()).isEqualTo(503);
        assertThat(failed.getResponse().getContentAsString()).contains("\"temporarily_unavailable\"");
        assertThat(signedInStill(session)).isTrue();

        assertThat(deliver(logoutToken).getResponse().getStatus()).isEqualTo(200);
        assertThat(signedInStill(session)).isFalse();
    }

    @Test
    @DisplayName("a session cannot be ended: 503 and the jti is not recorded, so the same delivery again ends the rest")
    void endingFails() throws Exception {
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());
        StubbornSession stubborn = new StubbornSession();
        assertThat(signIns.opened(stubborn, FAKE.issuer(), person, null)).isTrue();
        String logoutToken = token(spec -> spec.subject = person);

        assertThat(deliver(logoutToken).getResponse().getStatus()).isEqualTo(503);

        assertThat(deliver(logoutToken).getResponse().getStatus()).as("not refused as a replay").isEqualTo(200);
        assertThat(stubborn.isInvalid()).isTrue();
        assertThat(signedInStill(session)).isFalse();
    }

    /** A session whose store fails the first time it is asked to end it. */
    private static final class StubbornSession extends MockHttpSession {

        private boolean refusedOnce;

        @Override
        public void invalidate() {
            if (!refusedOnce) {
                refusedOnce = true;
                throw new UncheckedIOException(new IOException("session store unavailable"));
            }
            super.invalidate();
        }
    }

    // --- not a browser endpoint ---------------------------------------------------------------------------

    @Test
    @DisplayName("server to server: no CSRF token, no Origin check; no session or cookie is created, read or set")
    void noBrowserMachinery() throws Exception {
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());

        MvcResult result = mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", token(spec -> spec.subject = person))
                        .header("Origin", "https://elsewhere.example")
                        .header("Sec-Fetch-Site", "cross-site"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getRequest().getSession(false)).as("no session created").isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).as("not even the CSRF cookie").isEmpty();
        assertThat(signedInStill(session)).isFalse();

        // A browser's session cookie sent along is not used for anything: its owner is not signed out by a bad
        // token, and is not who the request acts for.
        MockHttpSession bystander = signedIn(uuid(), uuid());
        MvcResult withCookie = mvc.perform(post(PATH).session(bystander).param("logout_token", "garbage"))
                .andReturn();
        assertThat(withCookie.getResponse().getStatus()).isEqualTo(400);
        assertThat(signedInStill(bystander)).isTrue();
    }

    @Test
    @DisplayName("only POST: any other method is a 405 naming POST, never an error")
    void onlyPost() throws Exception {
        MvcResult result = mvc.perform(get(PATH)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(405);
        assertThat(result.getResponse().getHeader("Allow")).isEqualTo("POST");
        assertThat(result.getResponse().getHeaders("Cache-Control")).containsExactly("no-store");
    }

    // --- a logout that overtakes its sign-in -----------------------------------------------------------

    @Test
    @DisplayName("a sign-in still on its way back when Workeasy360 ended it is refused (expired) and leaves no session")
    void signInOvertakenByItsLogout() throws Exception {
        String person = uuid();
        String sid = uuid();
        Started started = start(null);
        String code = FAKE.issueCode(started.authorization(), spec -> {
            spec.subject = person;
            spec.extra.put("sid", sid);
        });
        assertThat(deliver(token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        })).getResponse().getStatus()).isEqualTo(200);

        MvcResult callback = callback(started, code, started.authorization().state());

        assertThat(ssoError(callback)).isEqualTo("expired");
        assertThat(sessionOf(callback)).as("the session the sign-in created was ended at once").isNull();
        assertThat(mvc.perform(get("/api/auth/session").session(started.session())).andReturn()
                .getResponse().getStatus()).isEqualTo(401);

        // A later sign-in (a new Workeasy360 sign-in, a new sid) works as usual.
        assertThat(signedInStill(signedIn(person, uuid()))).isTrue();
    }

    // --- helpers -----------------------------------------------------------------------------------------

    private MockHttpSession signedIn(String subject, String sid) throws Exception {
        MvcResult result = signInFresh(null, spec -> {
            spec.subject = subject;
            if (sid != null) {
                spec.extra.put("sid", sid);
            }
        });
        assertThat(ssoError(result)).isNull();
        return sessionOf(result);
    }

    private static String token(Consumer<LogoutTokenSpec> customizer) throws Exception {
        LogoutTokenSpec spec = new LogoutTokenSpec();
        customizer.accept(spec);
        return FAKE.mintLogoutToken(spec);
    }

    /** As Workeasy360 delivers it: a form POST with no cookie, no CSRF token and no Origin. */
    private MvcResult deliver(String logoutToken) throws Exception {
        return mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", logoutToken))
                .andReturn();
    }

    private boolean signedInStill(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/v1/test/whoami").session(session)).andReturn().getResponse().getStatus() == 200;
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
