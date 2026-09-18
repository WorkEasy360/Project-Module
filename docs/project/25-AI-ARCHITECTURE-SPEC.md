# AI Architecture (P4) — Specification

## Status of this document

**Resolved for the boundary it defines; one open decision explicitly flagged and left
unresolved (§4).** `07-AI.md` and `08-ROADMAP.md` name AI capabilities and a safety flow, but
define no data model, no lifecycle, and — critically — no LLM provider. This document
establishes the architecture every other `26`-`27` AI spec builds on, and draws the one hard
line this batch cannot cross without an explicit approval: which LLM provider to call.

Tags: **[ESTABLISHED]** = direct application of an existing convention or explicit existing
documentation. **[RESOLVED — conservative]** = genuinely undefined; resolved conservatively.
**[OPEN DECISION]** = cannot be resolved without a product/infrastructure decision this
document has no authority to make.

## 1. Scope of P4 in this batch

`08-ROADMAP.md` P4 lists: AI Project Creation, AI Task Breakdown, AI Task Improvement, Project
Copilot, AI Recommendations, AI Risk Analysis, AI What-if. Every one of these fundamentally
requires generating natural-language or structured content from project context — there is no
honest deterministic substitute (P3 Intelligence already covers the deterministic analysis:
Project Health, Delay Detection, Dependency Analysis, Reports). This batch therefore splits P4
into what is architecturally buildable *without* a provider, and what is not:

- **Buildable now**: the recommendation data model, lifecycle, authorization, audit trail, and
  the provider *boundary* itself (a port, per §3) — none of this requires actually calling an
  LLM. See `26-AI-RECOMMENDATIONS-SPEC.md`.
- **Blocked on this document's §4 decision**: any endpoint that must return real generated
  content. In this deployment (no provider configured — confirmed by `pom.xml` §14 below),
  those endpoints will correctly and honestly fail with a clear "AI provider not configured"
  error rather than fabricate output. This is not a bug to fix later in this batch; it is the
  documented, correct behavior of an unconfigured integration (§3).
- **Explicitly excluded from this batch's data model**: "AI Project Creation" — creating a
  project *is* the mutating action itself, with no read-only recommendation-only subset (unlike
  Task Breakdown/Improvement, which can honestly stop at "here is a suggestion," Project
  Creation cannot). It requires both §4's provider decision and `27-AI-ACTIONS-SPEC.md`'s tool
  allowlist decision. Not modeled here.

## 2. Package placement — from existing, already-written architecture, not invented

**[ESTABLISHED]** `src/main/java/com/projectmodule/ai/package-info.java` already states, in the
existing codebase, before this document was written:

> This package therefore contains no repository, no `EntityManager` and no entity mapping. Its
> planned subpackages are `tools` (the only entry point AI code has), `authorization` and
> `validation`. AI tools build the same commands an HTTP request builds, so AI is authorized and
> validated on exactly the same path as a human user.

This fixes package placement without guessing:
- `AIRecommendation` (entity), its repository, and its application service live under
  `com.projectmodule.work` — the same place Decision/Risk/Issue/ProjectHealth already live,
  despite `intelligence/package-info.java` nominally claiming that bounded context; this
  codebase's own established practice (confirmed across every P2/P3 slice) is that
  intelligence-adjacent entities live in `work`, not in a same-named empty package. Placing
  `AIRecommendation` there is the one placement consistent with both what `ai/package-info.java`
  explicitly rules out (persistence in `ai`) and what this codebase actually does everywhere
  else.
- The **AI provider port** (§3) lives under `com.projectmodule.integration.port`, alongside
  `CalendarPort`/`ChatPort`/`NotificationPort`/`UserDirectoryPort` — an LLM provider is exactly
  the kind of external system those ports already abstract; this is applying an existing
  pattern to a new port, not introducing new infrastructure (rule 22).
- `com.projectmodule.ai` itself holds only the orchestration entry point (`AIRecommendationController`'s
  supporting service that gathers context and calls the provider) — kept thin, per
  `ai/package-info.java`'s own description.

## 3. The AI provider boundary — a port, like every other external integration

**[ESTABLISHED]** Mirrors the existing `integration.port`/`integration.adapter` pattern exactly:

```java
// integration/port/AIProviderPort.java
public interface AIProviderPort {
    AIProviderResponse generate(AIProviderRequest request);
}

// integration/port/AIProviderRequest.java
record AIProviderRequest(String recommendationType, String projectContext, String question)

// integration/port/AIProviderResponse.java
record AIProviderResponse(String title, String rationale, String payload)
```

`projectContext` is plain text assembled by the caller from *existing* read-only application
services (§ `26-AI-RECOMMENDATIONS-SPEC.md` §3) — the port itself does not touch a repository or
know what a "Task" or "Risk" is; it only ever sees text in, text out. This keeps the boundary
honest per rule 9/10 (AI must not directly access the database; AI must operate only through
approved tools) at the lowest level: even the provider abstraction cannot reach a repository.

**[ESTABLISHED]** Default adapter: `UnavailableAIProviderAdapter`, registered as the only
`AIProviderPort` bean while no real provider is configured. Unlike `NoopCalendarAdapter`/
`NoopDocumentAdapter` (which safely no-op, because "did nothing" is a valid, honest outcome for
a calendar sync), an AI provider cannot safely "no-op" — returning empty/placeholder content
would be exactly the fake AI response rule 8 forbids. `UnavailableAIProviderAdapter.generate(...)`
therefore always throws the existing `IntegrationUnavailableException` ("AI provider is not
configured for this deployment") rather than returning anything. This is the honest behavior a
missing integration should have, and it reuses an exception type that already exists in this
module's sealed hierarchy — no new exception type is introduced.

## 4. [OPEN DECISION] Which LLM provider (or: none, by design)

This is the one decision this batch cannot make on its own, per the explicit instruction: *"Do
NOT choose or add an LLM provider merely to make the feature appear complete... Never invent LLM
providers or SDKs."* `pom.xml` (confirmed by direct inspection) has exactly 9 dependencies, none
of them AI/LLM-related. Three honest options, none selected here:

1. **No real provider in this codebase at all** — `AIProviderPort` stays permanently backed by
   `UnavailableAIProviderAdapter`; AI recommendation *requests* always fail cleanly with a clear
   error; the module is "AI-ready" (the whole pipeline up to the provider call is real,
   authorized, validated, audited) but not "AI-capable" in this deployment. This requires no new
   Maven dependency and no further approval.
2. **A specific provider is chosen and approved** (e.g., a named vendor's REST API called via
   `RestClient`/`WebClient`, already available transitively via `spring-boot-starter-web` — no
   new dependency needed for a plain HTTPS JSON call) — requires the product owner to name the
   provider, the API key/credential source (this module owns no secrets store; likely an
   externally-supplied configuration property), and confirm no SDK dependency is required beyond
   what's already present.
3. **A provider SDK dependency is explicitly requested and justified** — requires explicit
   Maven-dependency approval per rule 23 ("Do not add Maven dependencies unless... explicitly
   justified").

**This document does not choose between them.** Until this is answered, option 1 is what ships:
the recommendation pipeline is fully implemented and tested (`26-AI-RECOMMENDATIONS-SPEC.md`),
using `UnavailableAIProviderAdapter`, so that plugging in a real provider later is exactly one
new `AIProviderPort` bean — zero changes to authorization, validation, persistence, the API
contract, or tests other than substituting the adapter.

## 5. Actor type and authorization — already exist, reused exactly

**[ESTABLISHED]** `RequestContext.actorType()` already returns an `ActorType` enum with `AI` as
an existing value (`ActorType.java`, confirmed present before this document), explicitly
documented: *"AI and automation contexts are constructed internally, never taken from a
caller-supplied header, so a client cannot claim to be the AI in order to obtain different
treatment."* Every AI recommendation request in this batch is still **human-initiated** (a
person calls the API to request a recommendation) — the `RequestContext` therefore still carries
`ActorType.HUMAN` and the requesting person's real `userId`, exactly like any other API call.
`ActorType.AI` is reserved for a genuinely autonomous, non-human-initiated actor, which does not
exist anywhere in this batch (no scheduler, no autonomous trigger — see `29-AGENTS-SPEC.md` for
why P6 cannot introduce one either). No new authorization mechanism, no new permission: every
endpoint reuses `VIEW_PROJECT`/`EDIT_PROJECT` exactly as every other project-scoped feature does.

## 6. Failure behavior

**[ESTABLISHED, from the explicit instructions]** If `AIProviderPort.generate(...)` throws
(always, in this deployment), the requesting application service must:
- Not persist any `AIRecommendation` row (no partial/fake output).
- Let the exception propagate as a clear application error (`IntegrationUnavailableException`
  already maps to a `ProblemDetail` via the existing `GlobalExceptionHandler` — confirmed
  present in the sealed exception hierarchy).
- Not retry automatically (no retry behavior is specified anywhere).
- Leave all other project data untouched — the call is read-only until the moment of
  successful persistence, so there is nothing to roll back.

## 7. Out of scope (this document)

Choosing an LLM provider (§4), AIAction/AIApproval (`27-AI-ACTIONS-SPEC.md`), AI Project
Creation, any autonomous (non-human-initiated) AI invocation, prompt engineering/prompt
templates, conversation history/multi-turn context, token-usage tracking or cost accounting, any
AI-specific new permission.
