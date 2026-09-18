# Agents (P6) — Specification Proposal

## Status of this document

**Proposal — implementation blocked**, cascading from two decisions already left open earlier in
this batch. `08-ROADMAP.md` P6 lists four named agents: Project Manager Agent, Planning Agent,
Risk Agent, Documentation Agent. No other document defines what any of them does.

## 1. Why this is blocked, precisely

An Agent, per this task's own instructions, is *"controlled multi-step execution"* — distinct
from AI (a single suggestion) and Automation (a single deterministic rule firing once). Multi-
step execution requires **both**:

1. **Reasoning across steps** — deciding what to do next given the outcome of the previous step.
   This is exactly the LLM provider capability `25-AI-ARCHITECTURE-SPEC.md` §4 already left as
   an explicitly open decision, unresolved in this batch (no provider is configured — confirmed
   by `pom.xml`, unchanged from that document's audit).
2. **A defined set of tools it may invoke** — `27-AI-ACTIONS-SPEC.md` §1/§3 already identified
   this exact gap for single-step AI actions: no document names which existing application-
   service operations are safe to expose as an invocable tool, at what approval level, with what
   input shape. An agent needs the same allowlist, for potentially *several* tools across one
   multi-step plan — a strictly larger version of an already-open decision, not a new one.

Neither gap is specific to Agents; both were already surfaced by earlier documents in this
batch. This document does not re-derive them — it names the two named-agent roadmap items and
confirms each one requires both gaps closed before any code is possible:

- **Project Manager Agent** — would need to read broadly (already possible, see §2) and likely
  create/update tasks, risks, or issues — blocked on both gaps.
- **Planning Agent** — would need to reason about scheduling/dependencies and likely create
  phases/milestones/tasks — blocked on both gaps.
- **Risk Agent** — would need to reason about risk data and likely create/update risks — blocked
  on both gaps (its read-only half already exists as `RecommendationType.RISK_ANALYSIS`, see
  §2).
- **Documentation Agent** — would need to generate and likely post/store documentation —
  blocked on §4's provider gap at minimum, and on a document-storage integration contract this
  module does not have (`06-INTEGRATION.md` lists `Document` as an external reference only,
  through the existing `DocumentPort`, which is read-only — `findById` — with no write
  operation to post a generated document anywhere).

## 2. What already exists that an eventual agent could reuse

**[ESTABLISHED]** Not nothing — the read-only half of "agent-like" behavior already shipped in
this batch, under a different, more conservative name:
`AIRecommendationApplicationService`(`26-AI-RECOMMENDATIONS-SPEC.md`) already gathers
multi-source project context (tasks, risks, project summary, health) and can produce a single
structured recommendation once a provider exists. An agent's *first* step in any of the four
named roles would very likely look identical to requesting one of the existing
`RecommendationType`s. What an agent adds beyond that is exactly the two gaps in §1: reasoning
about what to do *next* based on that result, and being allowed to *act* on it, not just suggest.

## 3. What this document intentionally does not do

It does not propose an agent tool allowlist, an agent permission model, an agent execution-step
data model, or an agent approval flow ahead of §1's two gaps being resolved — doing so would be
guessing at a multi-step orchestration design without knowing what it is actually allowed to
orchestrate, which is exactly the invention this task's instructions forbid (rule 3/4/5; STOP
CONDITIONS: "an agent permission boundary is undefined," "implementing the feature would require
a significant architectural decision not documented anywhere").

## 4. Classification

**SPECIFICATION CREATED — IMPLEMENTATION BLOCKED.** No code accompanies this document. Unblocking
P6 requires, in order: (1) `25-AI-ARCHITECTURE-SPEC.md` §4 (an LLM provider decision), (2)
`27-AI-ACTIONS-SPEC.md` §3 (a mutating-tool allowlist, extended to cover multi-tool plans), (3) a
follow-up specification for whichever of the four named agents is prioritized first, defining
its concrete step sequence, tool set, and approval gates — none of which can be guessed from
`08-ROADMAP.md`'s four bare names.
