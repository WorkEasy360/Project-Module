# AI Actions & Approval (P4) — Specification Proposal

## Status of this document

**Proposal — open decisions, implementation blocked.** `01-SPEC.md`/`03-DATABASE.md` name
`AIAction` and `AIApproval` as owned entities; `07-AI.md`'s "AI Actions" leveling (Level 3:
"Approval required", Level 4: "Restricted / Destructive actions") and `05-EVENTS.md`'s
catalogued `ai.action.created`/`ai.approval.requested`/`ai.action.executed` events confirm this
capability is intended — but neither document, nor any other in `docs/project/`, names **which
existing operations an AI action is allowed to invoke.**

## 1. Why this is genuinely blocked, not merely undefined-and-guessable

`25-AI-ARCHITECTURE-SPEC.md` §2 already establishes, from the codebase's own existing
`ai/package-info.java`: *"AI tools build the same commands an HTTP request builds"* — i.e. an
`AIAction` must invoke an **existing** application-service method (`TaskApplicationService.
createTask`, `RiskApplicationService.archiveRisk`, etc.), exactly like a human-facing controller
does. That much is architecturally settled. What is missing is the **allowlist**: which of the
dozens of existing mutating application-service methods across Task/Risk/Issue/Decision/Phase/
Milestone/Dependency/Project are safe and intended for an AI action to invoke at all, with what
input shape, and at what approval level (07-AI.md Level 3 vs. Level 4).

This is a genuine product/security decision, not a business rule inferable from existing
documentation — the roadmap capability names (AI Task Breakdown, AI Task Improvement) *suggest*
task-creation/task-editing might belong on the allowlist, but naming a capability is not the
same as approving it as an autonomous-adjacent, AI-invocable mutation with a defined approval
gate. Guessing the allowlist would mean inventing exactly the kind of unreviewed AI-write-access
surface the instructions explicitly forbid (rule 5: never invent permissions; rule 22: don't
introduce infrastructure merely because it might be useful later; STOP CONDITIONS: "an agent
permission boundary is undefined" applies equally to an AI action boundary).

## 2. What is already settled, for whenever this is approved

**[ESTABLISHED]** So the eventual implementation is not blocked on architecture, only on the
allowlist:

- Flow: `AI request → AI reasoning → structured AIAction (PENDING_APPROVAL) → AIApproval (human
  decision) → only on APPROVED, invoke the allowlisted application-service method → EXECUTED |
  FAILED`. Never `AI → Repository → Database` (rule 10).
- `AIApproval` is a **separate** entity from `AIAction` (both named individually in
  `01-SPEC.md`/`03-DATABASE.md`), mirroring the existing "separate append-only audit/history
  record" pattern this module already uses twice (`ProjectActivity` alongside the entities it
  describes; `AutomationRun` alongside `ProjectAutomation`, `28-AUTOMATION-SPEC.md`) — not folded
  into `AIAction.status` as a same-entity field.
- Events already catalogued and reserved for this slice: `ai.action.created`,
  `ai.approval.requested`, `ai.action.executed` (`05-EVENTS.md`) — not raised by anything in this
  batch, since nothing creates an `AIAction` yet.
- Authorization: executing an approved action still goes through the *target* operation's own
  existing permission check (e.g. an approved "create task" action still requires
  `EDIT_PROJECT` at execution time, checked by `TaskApplicationService.createTask` itself,
  unchanged) — an `AIApproval` never substitutes for or bypasses the target operation's own
  authorization (rule 20/21).
- Destructive/high-impact actions (07-AI.md Level 4) require the same explicit-approval
  discipline as Level 3, with no automatic execution ever, per the Core Principle
  ("Human = Final authority").

## 3. [OPEN DECISION] The tool allowlist

For each candidate AI-invocable operation, someone with product authority must approve: the
exact application-service method, its input shape as an `AIAction` payload, and its approval
level (3 or 4). Candidates only — **none approved here**:
- Create a task (`TaskApplicationService.createTask`) — plausible Level 3, supports AI Task
  Breakdown's "apply" half.
- Update a task's plain fields (`TaskApplicationService.updateTask`) — plausible Level 3,
  supports AI Task Improvement's "apply" half.
- Create a risk/issue from an AI Risk Analysis finding — plausible Level 3.
- Archive/delete anything — plausible Level 4, or excluded entirely.
- Create a project (AI Project Creation) — plausible Level 4, or excluded entirely given its
  scope (creates an entire aggregate, not a single field).

## 4. [OPEN DECISION] Execution transaction/failure semantics

If an approved action's target operation throws (e.g. a stale-version `ConflictException`, or a
domain validation failure), does the `AIAction` move to `FAILED` with the error recorded (no
retry, per `25-AI-ARCHITECTURE-SPEC.md` §6's "do not silently retry unless explicitly
specified"), or is re-approval required to retry? Not specified anywhere; needs an explicit
answer once §3 is resolved.

## 5. Classification

**SPECIFICATION CREATED — IMPLEMENTATION BLOCKED.** No code accompanies this document. Blocked
on §3 (tool allowlist) and, transitively, on `25-AI-ARCHITECTURE-SPEC.md` §4 (LLM provider) for
any AI-*generated* action content — a human could in principle still manually request a
pre-approved action once §3 is resolved, independent of §4, but nothing in the current roadmap
describes a human-authored (non-AI-generated) `AIAction`, so this document does not split that
case out further.
