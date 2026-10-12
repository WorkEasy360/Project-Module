package com.projectmodule.security.entitlement;

import com.projectmodule.security.oidc.ProviderHttp;
import com.projectmodule.security.oidc.Workeasy360Provider;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One question to Workeasy360's entitlement API: may this person, in this organization, still use Easy
 * Project, and may they write?
 *
 * <p>There is no product parameter: the product is whoever the client assertion proves this is. The
 * <em>read</em> question is asked (no {@code write} parameter), as the other products do: Workeasy360 then
 * answers {@code allow} for a read-only organization (suspended subscription, read-only organization) with
 * {@code write_allowed: false}, whereas the write question is answered {@code deny}, which would lock the
 * organization out of reading too. Writability is taken from {@code write_allowed} in the answer.
 *
 * <p>Anything that is not an answer is one of two exceptions, and the difference matters:
 * {@link Unavailable} is an outage (5xx, 429, timeout, network, a misconfigured discovery, an unreadable
 * key) and is <em>never</em> a denial; {@link Refused} means Workeasy360 answered and refused this
 * product's own client (400, 401, 403: a revoked credential, a suspended integration), which must not
 * lapse into access.
 */
public class EntitlementClient {

    /** The decision document. */
    public record Answer(boolean allowed, boolean writeAllowed, String reason, Integer revalidateAfter) {
    }

    public static class Unavailable extends Exception {
        private static final long serialVersionUID = 1L;

        public Unavailable(String message) {
            super(message);
        }
    }

    /**
     * HTTP 429. Workeasy360 rate-limits failed client authentication, so right after a refusal a 429 is
     * most likely the same refusal seen through the limiter; {@link EntitlementService} decides which.
     */
    public static class RateLimited extends Unavailable {
        private static final long serialVersionUID = 1L;

        public RateLimited(String message) {
            super(message);
        }
    }

    public static class Refused extends Exception {
        private static final long serialVersionUID = 1L;

        public Refused(String message) {
            super(message);
        }
    }

    private final Workeasy360Provider provider;

    public EntitlementClient(Workeasy360Provider provider) {
        this.provider = provider;
    }

    public Answer ask(UUID organizationId, String subject) throws Unavailable, Refused {
        String endpoint;
        Map<String, String> form = new LinkedHashMap<>();
        try {
            endpoint = provider.metadataString("workeasy360_entitlement_endpoint");
            if (endpoint == null) {
                throw new Unavailable("discovery has no workeasy360_entitlement_endpoint");
            }
            form.put("organization_id", organizationId.toString());
            form.put("subject", subject);
            form.put("client_assertion_type", Workeasy360Provider.assertionType());
            form.put("client_assertion", provider.clientAssertion());
        } catch (Unavailable unavailable) {
            throw unavailable;
        } catch (RuntimeException exception) {
            // Discovery failed or the key is unreadable: not an answer, and not a denial.
            throw new Unavailable(exception.getClass().getSimpleName());
        }
        ProviderHttp.Response response;
        try {
            response = provider.http().postForm(endpoint, form);
        } catch (IOException exception) {
            throw new Unavailable("no response: " + exception.getClass().getSimpleName());
        }
        Object decision = response.body().get("decision");
        if (response.status() == 200 && ("allow".equals(decision) || "deny".equals(decision))) {
            boolean allowed = "allow".equals(decision);
            return new Answer(
                    allowed,
                    allowed && Boolean.TRUE.equals(response.body().get("write_allowed")),
                    response.body().get("reason") instanceof String reason ? clip(reason) : "",
                    response.body().get("revalidate_after") instanceof Number seconds ? seconds.intValue() : null);
        }
        String detail = "entitlement endpoint answered " + response.status();
        if (response.status() == 400 || response.status() == 401 || response.status() == 403) {
            throw new Refused(detail);
        }
        if (response.status() == 429) {
            throw new RateLimited(detail);
        }
        throw new Unavailable(detail);
    }

    private static String clip(String reason) {
        return reason.length() > 64 ? reason.substring(0, 64) : reason;
    }
}
