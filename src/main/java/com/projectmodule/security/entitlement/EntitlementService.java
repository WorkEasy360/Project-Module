package com.projectmodule.security.entitlement;

import com.projectmodule.security.Workeasy360Principal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Is this person's organization still entitled to Easy Project? Asked of Workeasy360 on a cadence, never
 * assumed (universal-SSO completion design section 4; same behaviour as the Easy CRM and EasyBook
 * clients).
 *
 * <ul>
 *   <li>One answer per (organization, person) &mdash; the {@code sub} the person signed in with &mdash; so
 *       Workeasy360 can also end access for a disabled account, a removed member or an unassigned seat. An
 *       {@code allow} is trusted for {@code min(revalidate_after, 900 s)} (at least 30 s). A denial is
 *       asked again after 60 s, so a renewal takes effect quickly.
 *   <li>An outage is never a denial: the last answer stands, with none access continues, and the next
 *       attempt waits 60 s.
 *   <li><strong>A refusal of this product's own client (400/401/403) is client-wide.</strong> A revoked
 *       credential or a suspended integration is no more true of one person than another, and Workeasy360
 *       rate-limits failed client authentication (429 after about ten a minute), so tracking it per person
 *       would let most people never learn of it. On a refusal the whole client is marked refused for 60 s:
 *       every question fails closed ({@code refused}) without asking. After 60 s one question is asked again
 *       while the rest still fail closed; a 429 near a refusal counts as the refusal.
 *   <li>A known denial does not lapse into access while it is in use: only a fresh answer replaces it. A
 *       denial nobody has used for longer than the session idle timeout is dropped &mdash; the session it
 *       belonged to cannot exist any more &mdash; and denials and allowances have separate bounds, so a flood of
 *       one cannot push out the other.
 *   <li>One request asks while the others use the last answer; a first check is never waved through because
 *       another request happens to be asking.
 * </ul>
 */
public class EntitlementService {

    static final int DEFAULT_REVALIDATE_SECONDS = 900;
    static final int MIN_REVALIDATE_SECONDS = 30;
    static final int DENIAL_RECHECK_SECONDS = 60;
    static final int FAILURE_BACKOFF_SECONDS = 60;
    static final int CLIENT_REFUSAL_SECONDS = 60;
    /** A 429 within this long of a refusal is taken to be that refusal (the failure limiter's window). */
    static final int REFUSAL_AFTERMATH_SECONDS = 300;
    /** Bound on remembered allowances, and separately on remembered denials. */
    static final int MAX_ENTRIES = 10_000;

    /** The reason recorded when Workeasy360 refused this product's own client. */
    public static final String REFUSED = "refused";
    /** The reason reported when no answer was ever available (an outage before the first check). */
    public static final String UNCHECKED = "unchecked";

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    /** What the request may do. */
    public record Access(boolean allowed, boolean writeAllowed, String reason) {

        public boolean refused() {
            return !allowed && REFUSED.equals(reason);
        }
    }

    private static final Access NOT_CHECKED = new Access(true, true, UNCHECKED);
    private static final Access CLIENT_REFUSED = new Access(false, false, REFUSED);

    private record Entry(Access access, long checkedAtMillis, long freshForMillis, long lastUsedMillis) {

        Entry used(long now) {
            return new Entry(access, checkedAtMillis, freshForMillis, now);
        }
    }

    private final EntitlementClient client;
    private final Clock clock;
    private final long denialIdleMillis;
    private final Object lock = new Object();
    /** Least-recently-used first. */
    private final Map<String, Entry> allowances = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Entry> denials = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Long> backoffUntil = new HashMap<>();
    private final Set<String> asking = ConcurrentHashMap.newKeySet();

    private volatile boolean clientRefused;
    private volatile long clientRefusedUntilMillis;
    private volatile long lastRefusalMillis = Long.MIN_VALUE / 2;
    private final AtomicBoolean probing = new AtomicBoolean();

    /**
     * @param denialIdleLimit a denial not looked at for this long is forgotten (the session idle timeout)
     */
    public EntitlementService(EntitlementClient client, Clock clock, Duration denialIdleLimit) {
        this.client = client;
        this.clock = clock;
        this.denialIdleMillis = denialIdleLimit.toMillis();
    }

    public Access accessFor(Workeasy360Principal principal) {
        long now = clock.millis();
        boolean probe = false;
        if (clientRefused) {
            if (now < clientRefusedUntilMillis) {
                return CLIENT_REFUSED;
            }
            // The minute is over: exactly one question finds out whether it still holds.
            probe = probing.compareAndSet(false, true);
            if (!probe) {
                return CLIENT_REFUSED;
            }
        }
        try {
            return decide(principal, now, probe);
        } finally {
            if (probe) {
                probing.set(false);
            }
        }
    }

    private Access decide(Workeasy360Principal principal, long now, boolean probe) {
        String key = principal.organizationId() + ":" + principal.subject();
        Entry known = known(key, now);
        if (probe) {
            return ask(principal, key, known, now);
        }
        if (known != null && now - known.checkedAtMillis() < known.freshForMillis()) {
            return known.access();
        }
        if (backingOff(key, now)) {
            // The provider failed a moment ago: the last answer stands, or (none yet) access continues.
            return known != null ? known.access() : NOT_CHECKED;
        }
        boolean mine = asking.add(key);
        if (!mine && known != null) {
            return known.access();
        }
        try {
            return ask(principal, key, known, now);
        } finally {
            if (mine) {
                asking.remove(key);
            }
        }
    }

    private Access ask(Workeasy360Principal principal, String key, Entry known, long now) {
        try {
            EntitlementClient.Answer answer = client.ask(principal.organizationId(), principal.subject());
            clientRefused = false;
            Access access = new Access(answer.allowed(), answer.writeAllowed(), answer.reason());
            remember(key, new Entry(access, now, freshFor(answer), now), known);
            return access;
        } catch (EntitlementClient.Refused refused) {
            return refuse(principal, key, known, now, refused.getMessage());
        } catch (EntitlementClient.RateLimited limited) {
            if (clientRefused || now - lastRefusalMillis < REFUSAL_AFTERMATH_SECONDS * 1000L) {
                // The failure limiter answering after a refusal: the same refusal, seen through it.
                return refuse(principal, key, known, now, limited.getMessage() + " (after a refusal)");
            }
            return outage(principal, key, known, now, limited);
        } catch (EntitlementClient.Unavailable unavailable) {
            return outage(principal, key, known, now, unavailable);
        }
    }

    private Access refuse(Workeasy360Principal principal, String key, Entry known, long now, String detail) {
        // Workeasy360 answered and refused our own client: not an outage, and not about this person only.
        log.error("workeasy360.entitlement_refused organization={} detail={}",
                principal.organizationId(), detail);
        clientRefusedUntilMillis = now + CLIENT_REFUSAL_SECONDS * 1000L;
        lastRefusalMillis = now;
        clientRefused = true;
        // Deliberately not remembered per person: the client-wide flag is what closes everybody, and it
        // clears itself the moment Workeasy360 answers again; a per-person copy would outlive it.
        return CLIENT_REFUSED;
    }

    private Access outage(Workeasy360Principal principal, String key, Entry known, long now,
                          EntitlementClient.Unavailable outage) {
        // Never a denial. While it lasts, access changes made in Workeasy360 are not reaching us.
        log.warn("workeasy360.entitlement_unavailable organization={} detail={} lastKnown={}",
                principal.organizationId(), outage.getMessage(),
                known == null ? "none" : known.access().allowed() ? "allow" : "deny");
        if (clientRefused) {
            // The probe could not tell either way: stay closed and try again in a minute.
            clientRefusedUntilMillis = now + CLIENT_REFUSAL_SECONDS * 1000L;
            return CLIENT_REFUSED;
        }
        synchronized (lock) {
            backoffUntil.put(key, now + FAILURE_BACKOFF_SECONDS * 1000L);
            trimBackoffs(now);
        }
        return known != null ? known.access() : NOT_CHECKED;
    }

    private static long freshFor(EntitlementClient.Answer answer) {
        if (!answer.allowed()) {
            return DENIAL_RECHECK_SECONDS * 1000L;
        }
        int seconds = answer.revalidateAfter() == null ? DEFAULT_REVALIDATE_SECONDS : answer.revalidateAfter();
        return Math.max(MIN_REVALIDATE_SECONDS, Math.min(seconds, DEFAULT_REVALIDATE_SECONDS)) * 1000L;
    }

    private Entry known(String key, long now) {
        synchronized (lock) {
            Entry denial = denials.get(key);
            if (denial != null) {
                if (now - denial.lastUsedMillis() > denialIdleMillis) {
                    denials.remove(key);
                    return null;
                }
                Entry used = denial.used(now);
                denials.put(key, used);
                return used;
            }
            return allowances.get(key);
        }
    }

    private boolean backingOff(String key, long now) {
        synchronized (lock) {
            Long until = backoffUntil.get(key);
            return until != null && until > now;
        }
    }

    private void remember(String key, Entry entry, Entry previous) {
        long now = entry.checkedAtMillis();
        synchronized (lock) {
            backoffUntil.remove(key);
            if (entry.access().allowed()) {
                denials.remove(key);
                allowances.put(key, entry);
                while (allowances.size() > MAX_ENTRIES) {
                    allowances.remove(allowances.keySet().iterator().next());
                }
            } else {
                allowances.remove(key);
                denials.put(key, entry);
                dropIdleDenials(now);
                while (denials.size() > MAX_ENTRIES) {
                    denials.remove(denials.keySet().iterator().next());
                }
            }
        }
        if (previous == null || previous.access().allowed() != entry.access().allowed()
                || !previous.access().reason().equals(entry.access().reason())) {
            log.info("workeasy360.entitlement_changed allowed={} reason={}",
                    entry.access().allowed(), entry.access().reason());
        }
    }

    /** Called holding {@code lock}. Eldest first, so it stops at the first denial still in use. */
    private void dropIdleDenials(long now) {
        Iterator<Entry> iterator = denials.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().lastUsedMillis() > denialIdleMillis) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private void trimBackoffs(long now) {
        if (backoffUntil.size() > MAX_ENTRIES) {
            backoffUntil.values().removeIf(until -> until <= now);
            if (backoffUntil.size() > MAX_ENTRIES) {
                backoffUntil.clear();
            }
        }
    }

    /** Forget everything (tests). */
    public void clear() {
        synchronized (lock) {
            allowances.clear();
            denials.clear();
            backoffUntil.clear();
        }
        asking.clear();
        clientRefused = false;
        clientRefusedUntilMillis = 0;
        lastRefusalMillis = Long.MIN_VALUE / 2;
        probing.set(false);
    }

    int allowanceCount() {
        synchronized (lock) {
            return allowances.size();
        }
    }

    int denialCount() {
        synchronized (lock) {
            return denials.size();
        }
    }
}
