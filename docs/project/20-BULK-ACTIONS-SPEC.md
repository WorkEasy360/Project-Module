# Bulk Actions (P2) — Specification

## Status of this document

**Resolved and implementation-ready**, scoped narrowly and explicitly. `08-ROADMAP.md:31` lists
`Bulk actions` under P2 — Views as a single bullet with no elaboration elsewhere.

Tags: **[ESTABLISHED]** = direct application of an existing convention.
**[RESOLVED — conservative]** = genuinely undefined; resolved by choosing the narrowest,
best-evidenced scope rather than guessing at a broader one.

## 1. Scope: bulk archive only

**[RESOLVED — conservative]** "Bulk actions" could mean many things (bulk create, bulk status
change, bulk reassignment, bulk archive, bulk delete). Rather than guess which ones are wanted,
this document picks the **one** operation that is: (a) already implemented and fully tested
individually for every entity in scope, (b) has no entity-specific business rule to reconcile
(no optimistic-lock version required — `archiveTask`/`archiveRisk`/`archiveIssue`/
`archiveDecision` all take just `(context, id)`, no `version` parameter), and (c) is safe and
reversible-in-spirit (archive is soft, never a hard delete, per this module's established
convention). That operation is **archive**. Bulk status changes, bulk reassignment, bulk create,
and hard delete are explicitly **out of scope** for this document — each would require its own
business-rule decisions (e.g. what happens when a bulk status change is invalid for one item but
valid for others) this document has no authority to invent.

**Entities**: Task, Risk, Issue, Decision — the same four entities Search/Filters/List already
scope themselves to, each of which already has an individually-tested `archiveX` application
service method.

## 2. Authorization and validation — no bypass

**[ESTABLISHED]** Each id in the batch is processed by calling the **existing**, unmodified
single-item archive method (`TaskApplicationService.archiveTask`,
`RiskApplicationService.archiveRisk`, `IssueApplicationService.archiveIssue`,
`DecisionApplicationService.archiveDecision`) — the exact same organization-boundary check,
`EDIT_PROJECT` authorization check, not-found handling, and already-archived handling as an
individual `DELETE` call. There is no separate, weaker "bulk" authorization path — this
satisfies the instruction that bulk operations must not create a privileged bypass.

## 3. Partial success, not all-or-nothing

**[RESOLVED — conservative]** Each id's archive call is **not** wrapped in one shared database
transaction across the whole batch — each existing `archiveX` method keeps its own
`@Transactional` boundary (unchanged), so one id failing (not found, wrong organization, already
archived) does not roll back any other id already archived in the same request. This mirrors how
a client would experience calling `DELETE` once per id today — bulk archive changes nothing
about *what* happens per item, only that one HTTP call triggers many of them.

## 4. API

**[RESOLVED — conservative]** One new endpoint per entity:
`POST /api/v1/projects/{projectId}/tasks/bulk-archive`
`POST /api/v1/projects/{projectId}/risks/bulk-archive`
`POST /api/v1/projects/{projectId}/issues/bulk-archive`
`POST /api/v1/projects/{projectId}/decisions/bulk-archive`

Request: `record BulkArchiveRequest(@NotEmpty @Size(max = 100) List<UUID> ids)` — a batch size
cap of 100 is an ordinary input-validation safeguard against an unbounded request body, not a
business rule; exceeding it is rejected with the existing `400 validation-failed` shape (the
same `@Valid`/`MethodArgumentNotValidException` path every other request DTO already uses, no
new exception-handling code).

Response: `record BulkArchiveResponse(List<BulkArchiveResultResponse> results)`,
`record BulkArchiveResultResponse(UUID id, boolean succeeded, String error)` — `error` is `null`
on success, otherwise the failing exception's message (reusing the message text the individual
`DELETE` endpoint's `ProblemDetail` would already have produced for that id).

`projectId` in the path is not itself re-validated against each id beyond what the existing
per-item archive call already does (it independently resolves the item's own project and
verifies the caller's organization boundary against *that* project, exactly like the individual
`DELETE` endpoint does) — an id belonging to a different project than the one in the URL simply
fails that item with the same `not-found`/`forbidden` semantics the individual endpoint would
give it; it is not silently reinterpreted as "belongs to this project."

## 5. Testing

**[ESTABLISHED]** Application-service tests (all succeed, partial failure — one missing id
among valid ones, both succeed independently — authorization enforced per item, batch size
limit), controller tests (response shape, 400 on empty/oversized `ids`), persistence-level
coverage is unnecessary beyond what each entity's existing archive persistence test already
covers (no new query is added — this feature only orchestrates existing calls).

## 6. Out of scope

Bulk create, bulk status/priority change, bulk reassignment, bulk un-archive/restore (no restore
capability exists for any entity in this module today), bulk operations on any entity outside
Task/Risk/Issue/Decision, cross-project batches.
