# ProjectComment — Proposed Specification (Pending Approval)

## Status of this document

**This is a proposal, not an approved specification**, following the same process
`09-DECISION-SPEC.md` established. `01-SPEC.md:31` and `03-DATABASE.md:17` document
`ProjectComment` only as a bare entity name — no fields, statuses, API, or events. Implementation
must not begin from this document until it is approved.

Tags used throughout:

- **[ESTABLISHED]** — a direct, mechanical application of a convention already in force for every
  comparable entity (Phase, Task, Risk, Issue, Decision). Not a new design choice.
- **[PROPOSED]** — new, invented for Comment because nothing in the docs addresses it, but with
  one clear best answer given existing conventions (low controversy).
- **[OPEN DECISION]** — genuinely undefined, with a real alternative that could reasonably be
  chosen instead. Not guessed; listed in full in §24 for explicit approval.

## 1. Purpose and scope

**[PROPOSED]** A `ProjectComment` is a short, unthreaded remark left on a project, for discussion
and context — not a task, not an audit entry, not a chat message. This spec deliberately keeps it
narrow (see §23, Non-goals): it is closer to `Risk`/`Issue`/`Decision` (a plain project-scoped
record) than to a messaging system.

## 2. Target scope — Project only

**[OPEN DECISION, recommended: Project only]** The entity is named `ProjectComment`, not
`Comment` — `01-SPEC.md:31` and `03-DATABASE.md:17` use that exact name, and no other document
ever generalizes it. Recommending comments attach **only to a Project**, not to Task/Risk/
Issue/Decision individually.

**Alternative:** a polymorphic target (`target_type` + `target_id`, or a comment table per
entity) letting any work item carry comments. Rejected as the recommendation because: (a) nothing
in any doc asks for it — the entity name itself argues against it; (b) it is exactly the kind of
generalization the request explicitly warned against ("do not expand into a generic discussion
system"); (c) it roughly triples the authorization/validation surface (which entity types are
commentable? does each need its own permission check?) for no documented requirement.

## 3. Entity fields and data types

| Field | Type | Required | Tag |
|---|---|---|---|
| `id` | UUID (v7) | yes | **[ESTABLISHED]** |
| `projectId` | UUID | yes, immutable | **[ESTABLISHED]** — same ownership convention as Phase/Task/Risk/Issue/Decision |
| `authorId` | UUID (opaque `ExternalUserId`) | yes, immutable | **[PROPOSED]** — see §4 |
| `body` | text | yes | **[PROPOSED]** — see §5 |
| `archivedAt` / `archivedBy` | timestamptz / UUID, nullable together | — | **[ESTABLISHED]** |
| `createdAt` / `updatedAt` / `version` | from `BaseEntity` | — | **[ESTABLISHED]** — an edit is visible as `updatedAt > createdAt`; no separate "edited" flag is proposed (would be a redundant field — see §23) |

No `status`, no `parentCommentId` (§9), no `title`/`subject` (a comment is body-only, unlike
every other entity which has a `name`).

## 4. Author representation

**[PROPOSED]** `authorId` uses the exact same opaque-reference pattern as
`Task.assigneeId`/`Decision.decidedBy`/`ProjectMember.userId`: a plain `UUID` column, no foreign
key, wrapped as `ExternalUserId` (`06-INTEGRATION.md` — User is owned by another module).

**Difference from `Decision.decidedBy`:** `decidedBy` is caller-*supplied* (the decider may not
be the API caller). `authorId` is **not** a request field — it is always set to
`context.requireUserId()` at creation time, because a comment's author is definitionally whoever
is calling the API to create it (nothing in any doc suggests posting a comment "on behalf of"
someone else, and allowing that would be a spoofing concern). This is a mechanical consequence of
the existing `RequestContext` identity model, not a new judgment call.

## 5. Comment body and validation limits

**[OPEN DECISION on length cap, recommended: no hard cap]** `body`: required, not blank
(`ValidationException` on blank, same as every `name` field's blank check). Recommending a `text`
column with **no maximum length**, consistent with every existing `description` field
(Task/Phase/Risk/Issue/Decision descriptions are uncapped `text` columns) — comments are
free-form remarks, and no document specifies a limit.

**Alternative:** cap at some size (e.g. 10,000 characters) to bound payload/row size. Flagged
because unlike `name` fields (which are capped at 200 everywhere), no precedent in this codebase
caps a `text` field, so either choice is a genuine addition, not a mechanical one.

## 6. Editing

**[OPEN DECISION — see §10]** Comments can be edited (`body` only, via `PATCH`). *Who* may edit
is an authorization question, addressed in §10, not a structural one. No edit history is kept
(§23, non-goal) — editing simply replaces `body` and bumps `updatedAt`/`version`.

## 7. Deletion / archiving

**[ESTABLISHED]** Same soft-archive convention as every entity: `DELETE /comments/{id}` calls
`archive(actorId)`, setting `archivedAt`/`archivedBy` together, guarded against double-archive,
excluded from active queries via `...AndArchivedAtIsNull`. No hard delete. *Who* may archive is
addressed in §10.

## 8. Lifecycle / status

**[ESTABLISHED, mirrors Issue/Decision]** None. A comment exists once posted; its only state
transition is archive. No `PROPOSED`/`DECIDED`-style status — nothing about a comment implies one.

## 9. Threading / replies

**[PROPOSED — recommended: none]** Comments are a **flat, chronological list per project**. No
`parentCommentId`, no nested replies, no reply-count. This is the single clearest instance of the
"narrowly scoped" instruction: threading is precisely what turns a comment log into a discussion/
chat system. If threaded replies are wanted, that is a deliberately separate, larger feature, not
part of this spec.

## 10. Authorization rules

**[ESTABLISHED]** List (view) → `ProjectPermission.VIEW_PROJECT`, reusing
`ProjectAuthorizationService` exactly as every entity does — no new permission is added to the
existing five (`VIEW_PROJECT`/`EDIT_PROJECT`/`ARCHIVE_PROJECT`/`MANAGE_MEMBERS`/
`MANAGE_SETTINGS`).

**[OPEN DECISION — create]** Recommended: `VIEW_PROJECT` (not `EDIT_PROJECT`). Every other
entity's mutations require `EDIT_PROJECT`, but a comment is participation/discussion, not project
content — a `VIEWER`-role stakeholder who can see the project arguably should be able to ask a
question via comment without needing edit rights over its structure. **Alternative (the more
convention-consistent choice):** require `EDIT_PROJECT` to create a comment too, exactly like
every other entity's create action. This is a genuine fork with real consequences for who can
participate, not a mechanical derivation either way.

**[OPEN DECISION — edit/delete]** Recommended: **the comment's author may edit or archive their
own comment; a caller with `EDIT_PROJECT` may also archive (but not edit the body of) any
comment**, as a moderation capability — removing inappropriate content is a project-management
action, but rewriting someone else's words is not. **Alternative:** author-only for both edit and
archive, with no moderation override at all; or, at the other extreme, `EDIT_PROJECT` holders can
edit/archive any comment exactly like every other entity (fully mechanical, but unusual for a
personal remark).

**Technical note:** either recommended option requires a new authorization shape this codebase
does not currently have — every existing entity's update/archive check is purely
permission-based (`requirePermission(userId, projectId, EDIT_PROJECT)`); "is the caller the
author of *this specific record*" is a new, additional check the application service would need
to perform once §10's open decisions are resolved.

## 11. Organization/project boundary rules

**[ESTABLISHED]** Identical to every entity: `projectId` is a plain column (no JPA association);
the organization boundary is enforced via
`ProjectApplicationService.findActiveProjectInCallerOrganization` before every operation, so a
comment can only be created under, listed from, or reached through a project the caller's
organization actually owns.

## 12. API endpoints and HTTP methods

**[PROPOSED, extends the established pattern]** `04-API.md` defines no Comment endpoints — the
same gap Risk/Issue/Decision had. Extending the identical pattern:

```
POST   /api/v1/projects/{projectId}/comments
GET    /api/v1/projects/{projectId}/comments
PATCH  /api/v1/comments/{id}
DELETE /api/v1/comments/{id}
```

No single-item `GET /comments/{id}`, matching Phase/Milestone/Risk/Issue/Decision (only `Task`
has one, because `04-API.md` documents it explicitly for Task and nowhere else).

## 13. Request/response DTO fields

**[PROPOSED]**

- `CreateCommentRequest`: `body` (required, not blank). No `authorId` field — see §4. No
  `projectId` — supplied by the path.
- `UpdateCommentRequest`: `body` (required, not blank — unlike other entities' `Update*Request`,
  there is no second optional field to leave unset, so `body` is not nullable-optional here),
  `version` (required, optimistic-lock check).
- `CommentResponse`: `id`, `projectId`, `authorId`, `body`, `archived`, `createdAt`, `updatedAt`,
  `version`.

## 14. Pagination and ordering for comment lists

**[OPEN DECISION]** Every work-management sub-entity list (Phase/Milestone/Task/Subtask/
Checklist/Dependency/Risk/Issue/Decision) returns a plain, unbounded `List<XResponse>` — no
pagination — because those collections are structurally small per project. Comments are
different: an active project can plausibly accumulate far more comments than phases or risks,
closer in growth pattern to `ProjectActivity` (the audit feed), which **is** paginated
(`ProjectActivityRepository.findByProjectIdOrderByOccurredAtDesc(UUID, Pageable)`,
`PageResponse<T>` in `common/api/PageResponse.java`, already used by `GET /projects`).

**Recommended:** reuse the existing `PageResponse<CommentResponse>` + Spring Data `Pageable`
convention (`GET /projects/{projectId}/comments?page=&size=&sort=`), the same as
`ProjectActivity`'s feed — this is an *established* convention elsewhere in the codebase, just
not yet applied to any work-management sub-entity, which is why it's flagged rather than treated
as purely mechanical.

**Alternative:** the simpler unbounded `List<CommentResponse>` every other sub-entity uses, sorted
`createdAt ASC` (oldest-first — recommended ordering either way, since a comment thread reads
naturally top-to-bottom chronologically, the same `...OrderByCreatedAtAsc` convention every
sub-entity list already uses — unlike `ProjectActivity`'s newest-first feed ordering).

## 15. Optimistic locking requirements

**[ESTABLISHED]** `version` required in `UpdateCommentRequest`; the application service compares
it to the loaded entity's version and throws `ConflictException` on mismatch before applying any
change, backed by Hibernate's `@Version` flush-time check as the secondary defense — identical to
every other `update*` use case.

## 16. Database schema, constraints, indexes, foreign keys

**[ESTABLISHED shape]** A new `V8__create_project_comments.sql` migration (V1–V7 already taken).
Table named `project_comments` (matching the entity name `ProjectComment` exactly, the same way
`decisions`/`risks`/`issues` match their entity names):

```
project_comments
  id               uuid         PK
  project_id       uuid         NOT NULL, FK -> projects(id) ON DELETE CASCADE
  author_id        uuid         NOT NULL
  body             text         NOT NULL
  archived_at      timestamptz
  archived_by      uuid
  created_at       timestamptz  NOT NULL
  updated_at       timestamptz  NOT NULL
  version          bigint       NOT NULL DEFAULT 0

  CHECK (length(btrim(body)) > 0)
  CHECK ((archived_at IS NULL) = (archived_by IS NULL))

  INDEX (project_id)
  INDEX (project_id) WHERE archived_at IS NULL
```

No FK on `author_id` (opaque reference to the User module, same as `tasks.assignee_id`/
`decisions.decided_by`). No `status` column (§8). No `parent_comment_id` column (§9).

## 17. Soft-delete/archive behavior

**[ESTABLISHED]** Covered in §7 — identical to every entity's soft-archive convention. No
distinct behavior proposed for Comment beyond that convention.

## 18. Domain events

**[OPEN DECISION, recommended: none]** `05-EVENTS.md` has no `## Comment`/`## ProjectComment`
section — zero events documented, the same gap Issue and Decision had. Recommending the same
resolution: no domain events, no `WorkEventRecorder` integration.

**Alternative:** add `comment.created` (at minimum), since comments are inherently
collaboration-visible activity other participants might reasonably expect to see in the project's
activity feed. Flagged as open rather than silently applying the Issue/Decision precedent again,
because unlike Issue/Decision, a *notification* use case plausibly exists for comments in a way
it didn't for either of those (see §20) — even though building that notification is explicitly
out of scope here (§23).

## 19. Outbox/audit behavior

**[PROPOSED, follows §18]** No outbox messages, no `ProjectActivity` audit rows — a direct
consequence of §18, not a separate decision. If §18's alternative is approved instead, this
section would need revisiting alongside it.

## 20. Integration implications

**[ESTABLISHED, with an explicit non-goal]** `authorId` is an opaque reference into the User
module (§4), identical to existing patterns — no new port or adapter. **Explicitly out of
scope for this spec:** routing comment creation through the already-implemented `NotificationPort`
to alert other project members. That would be a real, reasonable feature, but it is a distinct
capability layered on top of a working Comment CRUD, not a prerequisite for it, and building it
now would be exactly the scope creep the request warned against.

## 21. Error cases

**[ESTABLISHED]** Same `ProblemDetail`/exception taxonomy as every entity, via
`GlobalExceptionHandler`:

- Blank `body` → `ValidationException` → 400
- Unknown `projectId` (create/list) or `commentId` (update/archive) → `ResourceNotFoundException` → 404
- Stale `version` on update → `ConflictException` → 409
- Caller lacks the required permission (§10) → `AuthorizationException` → 403
- Caller is not the comment's author where authorship is required (§10, once resolved) →
  `AuthorizationException` → 403

## 22. Test requirements

**[ESTABLISHED shape]** The same four-layer suite every entity has:

- `ProjectCommentTest` (domain unit): creation, blank-body rejection, edit, archive guard
  (double-archive, edits-after-archive rejected).
- `ProjectCommentApplicationServiceTest` (Mockito): create (author = caller), list
  (paginated or not, per §14), update (author-permission branch once §10 is resolved,
  stale-version rejection, not-found), archive (author + moderator branches once §10 is
  resolved).
- `ProjectCommentControllerTest` (`@WebMvcTest`): all four endpoints, validation-rejection cases.
- `ProjectCommentPersistenceTest` (`@DataJpaTest`, **real PostgreSQL**,
  `@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", ...)`, run and confirmed
  actually executing, not skipped, the same discipline used for `DecisionPersistenceTest`):
  round-trip, FK enforcement (`project_id`), blank-body `CHECK` constraint rejection,
  archive round-trip, active-listing exclusion (and page-ordering, if §14's paginated option is
  approved).

If §18 is approved as "no events" (recommended), no `WorkEventRecorder` mock is needed and
nothing is added to `WorkEventRecorderTest`, matching `IssueApplicationServiceTest`/
`DecisionApplicationServiceTest`.

## 23. Explicit non-goals

To keep this narrowly scoped, as instructed, the following are **not** part of this
specification and would each need their own separate proposal if wanted later:

- Threaded replies / nested comments (§9).
- Reactions, emoji, likes/upvotes.
- @mentions or any notification/email integration (§20) — including via the existing
  `NotificationPort`.
- Rich text, Markdown rendering, or attachments/embedded files.
- Edit history / revision tracking (§3) — only the current `body` is stored.
- Read receipts or "seen by" tracking.
- Real-time delivery (WebSocket/SSE push) — comments are fetched via ordinary `GET`.
- Comments on any entity other than Project (§2) — Task/Risk/Issue/Decision comments are
  explicitly out of scope.
- Any AI summarization, moderation, or sentiment analysis of comment content (P4, out of scope
  per standing project instructions regardless).

## 24. Open decisions requiring explicit approval

1. **Target scope (§2).** Recommended: Project only. Alternative: polymorphic target
   (Task/Risk/Issue/Decision comments too).
2. **Body length cap (§5).** Recommended: none (uncapped `text`, matches `description`
   convention). Alternative: a specific character limit.
3. **Create-permission (§10).** Recommended: `VIEW_PROJECT`. Alternative: `EDIT_PROJECT`
   (fully mechanical, consistent with every other entity's create action).
4. **Edit/archive permission (§10).** Recommended: author edits/archives their own comment;
   an `EDIT_PROJECT` holder may additionally archive (moderate) any comment, but not edit its
   body. Alternatives: (a) author-only for both, no moderation override; (b) fully mechanical
   `EDIT_PROJECT`-holder-can-edit-or-archive-any-comment, like every other entity.
5. **Domain events / outbox (§18/§19).** Recommended: none (mirrors Issue/Decision).
   Alternative: add `comment.created` (and possibly `comment.updated`), not documented in
   `05-EVENTS.md`, purely invented.
6. **Pagination shape (§14).** Recommended: paginated (`PageResponse`/`Pageable`, reusing the
   `ProjectActivity`/`GET /projects` convention). Alternative: plain unbounded
   `List<CommentResponse>`, matching every other work-management sub-entity's list endpoint.
   (Ordering — oldest-first — is recommended either way and is lower-stakes than the
   pagination-shape choice itself.)

Nothing else in this document is a real fork — the rest is either a fixed codebase-wide
convention (§3 core fields, §4, §7, §8, §11, §12, §13, §15, §16, §17, §19, §21, §22) or a direct,
flagged consequence of one of the six questions above.

## Implementation-readiness

**Not yet implementation-ready.** Six open decisions (§24) require approval, the same checkpoint
`09-DECISION-SPEC.md` went through. Once resolved, every remaining section is a mechanical
application of existing convention, the same as every prior slice. No Java code, SQL, or tests
have been written; this file is the only change.
