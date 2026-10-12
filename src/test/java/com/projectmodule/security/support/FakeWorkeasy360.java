package com.projectmodule.security.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A stand-in for Workeasy360 for tests: a real HTTP server (the JDK's {@code HttpServer}) that speaks
 * enough of the provider side of the product contract to drive complete flows.
 *
 * <ul>
 *   <li>discovery (issuer = its own base URL), the key set, a token endpoint, the entitlement endpoint, an
 *       end-session endpoint address and a userinfo endpoint (which counts calls: it must never be called);
 *   <li>the token endpoint verifies what a real one would: the {@code private_key_jwt} client assertion
 *       (signature against the registered public key, {@code kid}, {@code iss}/{@code sub}, {@code aud} =
 *       the token endpoint, one-time {@code jti}, lifetime of at most 60 s), the redirect URI, a one-time
 *       code, and the PKCE verifier against the challenge it saw in the authorization request;
 *   <li>ID tokens are minted with Nimbus and can be bent in every way a test needs
 *       ({@link IdTokenSpec}).
 * </ul>
 */
public final class FakeWorkeasy360 {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static FakeWorkeasy360 shared;

    /** One server per JVM; contexts that cached its URL stay valid across test classes. */
    public static synchronized FakeWorkeasy360 shared() {
        if (shared == null) {
            shared = new FakeWorkeasy360();
        }
        return shared;
    }

    public static final String CLIENT_ID = "easy-project-test-client";
    public static final String REDIRECT_URI = "https://ops.workeasy360.com/api/auth/callback/workeasy360";
    public static final String POST_LOGOUT_URI = "https://ops.workeasy360.com/";

    /** What an authorization request carried (read from the redirect Location the application sent). */
    public record Authorization(String state, String nonce, String codeChallenge, String codeChallengeMethod,
                                String organization, String clientId, String redirectUri, String scope,
                                String responseType, Map<String, String> all) {
    }

    /** Every way an ID token can be shaped or broken. Defaults are a perfectly good token. */
    public static final class IdTokenSpec {
        public String issuer;
        public Object audience;
        public String subject;
        public String organizationId;
        public String productCode = "easy_project";
        public String typ = "id_token";
        public String nonce;
        public String jti = UUID.randomUUID().toString();
        public String email = "person@example.com";
        public String name = "Pat Person";
        public Integer aal = 1;
        public Instant authTime = Instant.now();
        public Instant issuedAt = Instant.now();
        public Instant notBefore;
        public Instant expiresAt = Instant.now().plusSeconds(300);
        public Set<String> omit = new HashSet<>();
        public Map<String, Object> extra = new LinkedHashMap<>();
        /** NORMAL, HS256_WITH_PUBLIC_KEY, NONE, UNKNOWN_KID, WRONG_KEY. */
        public String signing = "NORMAL";
        /** Sign with the EC key (ES256) instead of the RSA key. */
        public boolean useEc = false;
    }

    private record Code(Authorization authorization, Consumer<IdTokenSpec> customizer, boolean[] used) {
    }

    /** A scripted answer of the entitlement endpoint. */
    public record EntitlementReply(int status, Map<String, Object> body) {
        public static EntitlementReply allow() {
            return allow(900, true);
        }

        public static EntitlementReply allow(int revalidateAfter, boolean writeAllowed) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("decision", "allow");
            body.put("reason", "entitled");
            body.put("product_code", "easy_project");
            body.put("write_allowed", writeAllowed);
            body.put("revalidate_after", revalidateAfter);
            return new EntitlementReply(200, body);
        }

        public static EntitlementReply deny(String reason) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("decision", "deny");
            body.put("reason", reason);
            body.put("product_code", "easy_project");
            body.put("write_allowed", false);
            body.put("revalidate_after", 900);
            return new EntitlementReply(200, body);
        }

        public static EntitlementReply status(int status) {
            return new EntitlementReply(status, Map.of("error", "scripted"));
        }
    }

    public record EntitlementRequest(String organizationId, String subject, String write) {
    }

    /** A token-endpoint request, after client authentication. */
    public record TokenRequest(Map<String, String> form, Code code) {
    }

    private final HttpServer server;
    private final String base;
    private final RSAKey signingKey;
    private final ECKey ecSigningKey;
    private final RSAKey otherKey;
    private final List<JWK> registeredClientKeys = new ArrayList<>();

    private final Map<String, Code> codes = new ConcurrentHashMap<>();
    private final Set<String> seenAssertionIds = ConcurrentHashMap.newKeySet();

    public final List<String> assertionProblems = Collections.synchronizedList(new ArrayList<>());
    public final List<Map<String, String>> tokenRequests = Collections.synchronizedList(new ArrayList<>());
    public final List<EntitlementRequest> entitlementRequests = Collections.synchronizedList(new ArrayList<>());
    public volatile int userinfoCalls;
    public volatile int discoveryCalls;
    public volatile int jwksCalls;

    /** Hooks, reset between tests. */
    public volatile Function<TokenRequest, EntitlementReply> tokenOverride;
    public volatile Function<EntitlementRequest, EntitlementReply> entitlementHandler;
    public volatile boolean discoveryDown;
    public volatile String discoveryIssuerOverride;
    public volatile boolean jwksDown;
    public volatile boolean advertiseEndSession = true;
    /** Send the headers and a few bytes of the body of this endpoint's answer, then stall (see {@link #stall}). */
    public volatile boolean stallDiscovery;
    public volatile boolean stallJwks;
    public volatile boolean stallToken;
    public volatile boolean stallEntitlement;
    /** Answer the key-set request with a redirect to a key set somewhere else. */
    public volatile boolean jwksRedirects;
    public volatile int stalledRequests;
    /** Requests that arrived at the place a key-set redirect pointed to (must stay zero). */
    public volatile int followedRedirects;
    private final java.util.concurrent.CountDownLatch neverReleased = new java.util.concurrent.CountDownLatch(1);

    private FakeWorkeasy360() {
        try {
            this.signingKey = new RSAKeyGenerator(2048).keyID("idp-rsa-1").generate();
            this.ecSigningKey = new ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).keyID("idp-ec-1").generate();
            this.otherKey = new RSAKeyGenerator(2048).keyID("idp-rsa-1").generate();
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        this.base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/.well-known/openid-configuration", this::discovery);
        server.createContext("/jwks", this::jwks);
        server.createContext("/jwks-elsewhere", exchange -> {
            followedRedirects++;
            respondRaw(exchange, 200, new JWKSet(List.of(signingKey.toPublicJWK())).toString(true));
        });
        server.createContext("/token", this::token);
        server.createContext("/entitlement", this::entitlement);
        server.createContext("/userinfo", exchange -> {
            userinfoCalls++;
            respond(exchange, 200, Map.of("sub", "x"));
        });
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-workeasy360");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        reset();
    }

    public String issuer() {
        return base;
    }

    public String tokenEndpoint() {
        return base + "/token";
    }

    public String authorizationEndpoint() {
        return base + "/authorize";
    }

    public String endSessionEndpoint() {
        return base + "/logout";
    }

    public RSAKey idpRsaKey() {
        return signingKey;
    }

    /** Public keys of the client credential this fake accepts for client assertions. */
    public void registerClientKey(JWK publicOrPrivate) {
        synchronized (registeredClientKeys) {
            registeredClientKeys.removeIf(key -> key.getKeyID().equals(publicOrPrivate.getKeyID()));
            registeredClientKeys.add(publicOrPrivate.toPublicJWK());
        }
    }

    public void reset() {
        codes.clear();
        seenAssertionIds.clear();
        assertionProblems.clear();
        tokenRequests.clear();
        entitlementRequests.clear();
        userinfoCalls = 0;
        tokenOverride = null;
        entitlementHandler = request -> EntitlementReply.allow();
        discoveryDown = false;
        discoveryIssuerOverride = null;
        jwksDown = false;
        advertiseEndSession = true;
        stallDiscovery = false;
        stallJwks = false;
        stallToken = false;
        stallEntitlement = false;
        jwksRedirects = false;
        followedRedirects = 0;
        stalledRequests = 0;
    }

    /**
     * A server that sends its headers and the start of the body, then goes quiet: the JDK's per-request
     * timeout stops at the headers, so only an overall deadline notices.
     */
    private void stall(HttpExchange exchange) throws IOException {
        stalledRequests++;
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 100_000);
        exchange.getResponseBody().write("{\"issuer\":".getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
        try {
            neverReleased.await(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        exchange.close();
    }

    // --- driving a flow ---------------------------------------------------------------------------

    /** Reads the authorization request out of the Location the application redirected the browser to. */
    public Authorization parse(String location) {
        URI uri = URI.create(location);
        if (!(base + "/authorize").equals(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath())) {
            throw new AssertionError("not redirected to the authorization endpoint: " + location);
        }
        Map<String, String> all = form(uri.getRawQuery());
        return new Authorization(all.get("state"), all.get("nonce"), all.get("code_challenge"),
                all.get("code_challenge_method"), all.get("organization"), all.get("client_id"),
                all.get("redirect_uri"), all.get("scope"), all.get("response_type"), all);
    }

    /** The provider's side of a successful sign-in: remember the request, hand out a one-time code. */
    public String issueCode(Authorization authorization, Consumer<IdTokenSpec> customizer) {
        String code = "code-" + UUID.randomUUID();
        codes.put(code, new Code(authorization, customizer == null ? spec -> { } : customizer, new boolean[1]));
        return code;
    }

    // --- endpoints --------------------------------------------------------------------------------

    private void discovery(HttpExchange exchange) throws IOException {
        discoveryCalls++;
        if (stallDiscovery) {
            stall(exchange);
            return;
        }
        if (discoveryDown) {
            respond(exchange, 503, Map.of("error", "temporarily_unavailable"));
            return;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("issuer", discoveryIssuerOverride != null ? discoveryIssuerOverride : base);
        document.put("authorization_endpoint", base + "/authorize");
        document.put("token_endpoint", base + "/token");
        document.put("jwks_uri", base + "/jwks");
        document.put("userinfo_endpoint", base + "/userinfo");
        if (advertiseEndSession) {
            document.put("end_session_endpoint", base + "/logout");
        }
        document.put("workeasy360_entitlement_endpoint", base + "/entitlement");
        document.put("response_types_supported", List.of("code"));
        document.put("subject_types_supported", List.of("public"));
        document.put("id_token_signing_alg_values_supported", List.of("RS256", "ES256"));
        document.put("token_endpoint_auth_methods_supported", List.of("private_key_jwt"));
        document.put("scopes_supported", List.of("openid", "email", "profile", "organization"));
        document.put("code_challenge_methods_supported", List.of("S256"));
        respond(exchange, 200, document);
    }

    private void jwks(HttpExchange exchange) throws IOException {
        jwksCalls++;
        if (stallJwks) {
            stall(exchange);
            return;
        }
        if (jwksRedirects) {
            exchange.getResponseHeaders().set("Location", base + "/jwks-elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        if (jwksDown) {
            respond(exchange, 503, Map.of("error", "down"));
            return;
        }
        JWKSet set = new JWKSet(List.of(signingKey.toPublicJWK(), ecSigningKey.toPublicJWK()));
        respondRaw(exchange, 200, set.toString(true));
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        tokenRequests.add(form);
        if (stallToken) {
            stall(exchange);
            return;
        }
        if (!clientAuthenticated(form, tokenEndpoint())) {
            respond(exchange, 401, Map.of("error", "invalid_client"));
            return;
        }
        Code code = codes.get(form.get("code"));
        Function<TokenRequest, EntitlementReply> override = tokenOverride;
        if (override != null) {
            EntitlementReply reply = override.apply(new TokenRequest(form, code));
            if (reply != null) {
                respond(exchange, reply.status(), reply.body());
                return;
            }
        }
        if (!"authorization_code".equals(form.get("grant_type")) || code == null) {
            respond(exchange, 400, Map.of("error", "invalid_grant"));
            return;
        }
        synchronized (code.used()) {
            if (code.used()[0]) {
                // A replayed code.
                respond(exchange, 400, Map.of("error", "invalid_grant"));
                return;
            }
            code.used()[0] = true;
        }
        Authorization authorization = code.authorization();
        if (!authorization.redirectUri().equals(form.get("redirect_uri"))) {
            respond(exchange, 400, Map.of("error", "invalid_grant", "error_description", "redirect_uri"));
            return;
        }
        if (!authorization.clientId().equals(form.get("client_id") == null
                ? authorization.clientId() : form.get("client_id"))) {
            respond(exchange, 400, Map.of("error", "invalid_grant"));
            return;
        }
        String verifier = form.get("code_verifier");
        if (verifier == null || !s256(verifier).equals(authorization.codeChallenge())) {
            respond(exchange, 400, Map.of("error", "invalid_grant", "error_description", "PKCE"));
            return;
        }
        IdTokenSpec spec = new IdTokenSpec();
        spec.issuer = base;
        spec.audience = authorization.clientId();
        spec.subject = UUID.randomUUID().toString();
        spec.organizationId = authorization.organization() != null
                ? authorization.organization() : UUID.randomUUID().toString();
        spec.nonce = authorization.nonce();
        code.customizer().accept(spec);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", "access-" + UUID.randomUUID());
        response.put("token_type", "Bearer");
        response.put("expires_in", 300);
        response.put("scope", authorization.scope());
        try {
            response.put("id_token", mint(spec));
        } catch (Exception exception) {
            throw new IOException(exception);
        }
        respond(exchange, 200, response);
    }

    private void entitlement(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (stallEntitlement) {
            stall(exchange);
            return;
        }
        if (!clientAuthenticated(form, tokenEndpoint())) {
            respond(exchange, 401, Map.of("error", "invalid_client"));
            return;
        }
        EntitlementRequest request = new EntitlementRequest(
                form.get("organization_id"), form.get("subject"), form.get("write"));
        entitlementRequests.add(request);
        EntitlementReply reply = entitlementHandler.apply(request);
        // Workeasy360's real semantics: the write question is answered deny for a read-only (suspended)
        // organization, while the read question (no write parameter) is allow with write_allowed false.
        if ("allow".equals(reply.body().get("decision")) && Boolean.FALSE.equals(reply.body().get("write_allowed"))
                && "true".equalsIgnoreCase(request.write())) {
            reply = EntitlementReply.deny("suspended");
        }
        respond(exchange, reply.status(), reply.body());
    }

    /** private_key_jwt: signature, kid, iss = sub = client id, aud = token endpoint, one-time jti, at most 60 s. */
    private boolean clientAuthenticated(Map<String, String> form, String expectedAudience) {
        try {
            if (!"urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(form.get("client_assertion_type"))) {
                assertionProblems.add("wrong client_assertion_type");
                return false;
            }
            SignedJWT assertion = SignedJWT.parse(form.get("client_assertion"));
            String kid = assertion.getHeader().getKeyID();
            JWK key;
            synchronized (registeredClientKeys) {
                key = registeredClientKeys.stream().filter(k -> k.getKeyID().equals(kid)).findFirst().orElse(null);
            }
            if (key == null) {
                assertionProblems.add("unknown kid " + kid);
                return false;
            }
            boolean verified = key instanceof RSAKey rsa
                    ? assertion.verify(new RSASSAVerifier(rsa))
                    : assertion.verify(new ECDSAVerifier((ECKey) key));
            if (!verified) {
                assertionProblems.add("bad signature");
                return false;
            }
            JWTClaimsSet claims = assertion.getJWTClaimsSet();
            if (!CLIENT_ID.equals(claims.getIssuer()) || !CLIENT_ID.equals(claims.getSubject())) {
                assertionProblems.add("iss/sub is not the client id");
                return false;
            }
            if (claims.getAudience().size() != 1 || !expectedAudience.equals(claims.getAudience().get(0))) {
                assertionProblems.add("aud is not the token endpoint: " + claims.getAudience());
                return false;
            }
            if (claims.getJWTID() == null || !seenAssertionIds.add(claims.getJWTID())) {
                assertionProblems.add("jti missing or reused");
                return false;
            }
            if (claims.getExpirationTime() == null || claims.getIssueTime() == null
                    || claims.getExpirationTime().getTime() - claims.getIssueTime().getTime() > 60_000
                    || claims.getExpirationTime().before(new Date())) {
                assertionProblems.add("lifetime is not within 60 s");
                return false;
            }
            return true;
        } catch (Exception exception) {
            assertionProblems.add("unparseable client assertion: " + exception);
            return false;
        }
    }

    // --- minting ------------------------------------------------------------------------------------

    public String mint(IdTokenSpec spec) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        put(claims, spec, "iss", spec.issuer);
        put(claims, spec, "sub", spec.subject);
        put(claims, spec, "aud", spec.audience);
        put(claims, spec, "iat", spec.issuedAt == null ? null : Date.from(spec.issuedAt));
        put(claims, spec, "exp", spec.expiresAt == null ? null : Date.from(spec.expiresAt));
        put(claims, spec, "nbf", spec.notBefore == null ? null : Date.from(spec.notBefore));
        put(claims, spec, "jti", spec.jti);
        put(claims, spec, "typ", spec.typ);
        put(claims, spec, "nonce", spec.nonce);
        put(claims, spec, "organization_id", spec.organizationId);
        put(claims, spec, "product_code", spec.productCode);
        put(claims, spec, "aal", spec.aal);
        put(claims, spec, "auth_time", spec.authTime == null ? null : spec.authTime.getEpochSecond());
        put(claims, spec, "email", spec.email);
        put(claims, spec, "name", spec.name);
        spec.extra.forEach(claims::claim);
        JWTClaimsSet set = claims.build();

        switch (spec.signing) {
            case "NONE":
                return new PlainJWT(set).serialize();
            case "HS256_WITH_PUBLIC_KEY": {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .keyID(signingKey.getKeyID()).build(), set);
                // The classic confusion: the RSA public key, as bytes, used as an HMAC secret.
                byte[] secret = signingKey.toRSAPublicKey().getEncoded();
                byte[] padded = secret.length >= 32 ? secret : Base64.getEncoder().encode(secret);
                jwt.sign(new MACSigner(padded));
                return jwt.serialize();
            }
            case "NO_KID": {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), set);
                jwt.sign(new RSASSASigner(signingKey));
                return jwt.serialize();
            }
            case "UNKNOWN_KID": {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("no-such-key").build(), set);
                jwt.sign(new RSASSASigner(signingKey));
                return jwt.serialize();
            }
            case "WRONG_KEY": {
                // Right kid, signed by somebody else's private key.
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(signingKey.getKeyID()).build(), set);
                jwt.sign(new RSASSASigner(otherKey));
                return jwt.serialize();
            }
            default: {
                JWSSigner signer;
                JWSHeader header;
                if (spec.useEc) {
                    signer = new ECDSASigner(ecSigningKey);
                    header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecSigningKey.getKeyID()).build();
                } else {
                    signer = new RSASSASigner(signingKey);
                    header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build();
                }
                SignedJWT jwt = new SignedJWT(header, set);
                jwt.sign(signer);
                return jwt.serialize();
            }
        }
    }

    private static void put(JWTClaimsSet.Builder builder, IdTokenSpec spec, String name, Object value) {
        if (value != null && !spec.omit.contains(name)) {
            builder.claim(name, value);
        }
    }

    // --- plumbing -----------------------------------------------------------------------------------

    static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static Map<String, String> form(String encoded) {
        Map<String, String> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static void respond(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        respondRaw(exchange, status, JSON.writeValueAsString(body));
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        respondRaw(exchange, status, JSON.writeValueAsString(body));
    }

    private static void respondRaw(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
