# ProjectTemplate — Approved Specification

## Status of this document

**Approved.** All six open decisions below were presented and confirmed by the project owner,
each choosing the recommended option — the original four from this document's approval pass, plus
two more (#5, #6) added at a post-implementation checkpoint review, once the implementation
surfaced two points this document had not explicitly settled (pagination shape for `GET
/templates`, and the null-`defaultPriority`-on-apply rule). Following the same process `09`–`11`
established:
`01-SPEC.md:34` and `03-DATABASE.md:20` document `ProjectTemplate` only as a bare entity name —
no fields, relationships, API, or events, and like `Decision`, it is not listed anywhere in
`08-ROADMAP.md`. It is now this module's specification for ProjectTemplate, to be followed
exactly the same way `01-SPEC.md` through `11-CUSTOMFIELD-SPEC.md` are — implementation may
proceed from it once explicitly requested.

**Revised** from the first draft, before approval: §3 was settled to the V1 metadata-only scope,
with structural Phase/Task snapshots explicitly labeled out-of-scope/future-enhancement rather
than an open option; §6 (Authorization) was corrected to remove an unsupported claim that
possession of `RequestContext` itself proves organization membership — it does not, per
`RequestContext`'s own Javadoc.

This is the **largest design surface in this document chain so far**. Every prior spec (`09`,
`10`, `11`) proposed a single, self-contained, project-scoped record. `ProjectTemplate` is
different in two structural ways, not just a bigger field list:

1. It is the **first organization-scoped entity** proposed outside `Project` itself — reusable
   across many projects, so it cannot be scoped to one.
2. Its core purpose — a *blueprint for creating a Project* — implies an **apply/instantiate
   workflow** that reaches into `ProjectApplicationService`, not just its own CRUD.

Tags used throughout, identical to `09`–`11`:

- **[ESTABLISHED]** — a direct, mechanical application of a convention already in force
  elsewhere in this codebase (including `Project` itself, not only the work-management
  sub-entities `09`–`11` compared against).
- **[PROPOSED]** — new, invented for Template because nothing in the docs addresses it, but with
  one clear best answer given existing conventions (low controversy).
- **[APPROVED]** — was an **[OPEN DECISION]** below, now confirmed by the project owner. The
  surrounding text is left as originally proposed (it already described the approved,
  recommended option); see the final section for the confirmed outcome of each.

## 1. Purpose

**[PROPOSED, per explicit direction]** A `ProjectTemplate` is a reusable blueprint an
organization can apply to create a new `Project` pre-configured with common settings, avoiding
repetitive manual setup for recurring project types (e.g. "Standard Sprint," "Client Onboarding").
No document states this; it is the ordinary meaning of "project template," and the framing this
proposal was explicitly directed to use in the absence of contrary evidence — none was found.

## 2. Scope — organization, not project

**[ESTABLISHED, by direct analogy to `Project` itself]** A template must outlive and be reusable
across many projects, so it cannot carry a `project_id` the way every other entity in `09`–`11`
does. `Project` itself already establishes the organization-scoping convention this reuses
exactly: `projects.organization_id` is a plain `uuid` column with **no foreign key** (Organization
is owned by another module — `06-INTEGRATION.md`), and `ProjectApplicationService.createProject`/
`listProjects` resolve the boundary via `requireOrganizationId(context)`, not a per-record
permission lookup. `ProjectTemplate` follows the identical shape: `organization_id`, no FK,
boundary enforced the same way.

This is the one point in this document that is closer to **[ESTABLISHED]** than
**[PROPOSED]**, despite being new for every *sub*-entity so far — the convention already exists,
just one level up, on `Project` itself.

## 3. Content / relationships — the central open question

**[DECIDED for V1 — metadata-only]** What a template stores is not derivable from any document
and has no analogue among `09`–`11` (all of which had an obvious minimal shape). **V1's purpose
is a reusable project-default preset — not a full structural project blueprint.** The template
stores only `Project`-level defaults: its own `name` (the template's label, e.g. "Standard
Sprint"), `description` (what it's for), and `defaultPriority` (a `ProjectPriority`, pre-filling
the one field every `Project` already requires on creation). Applying it is materially equivalent
to "create a project with these defaults pre-filled." **No child tables, no snapshot of
Phases/Milestones/Tasks in V1.**

**Explicitly OUT OF SCOPE for V1 — recorded as a possible future enhancement, not an open
question:**

- **A shallow structural snapshot** — an ordered list of phase names/descriptions
  (`project_template_phases`, a name + description + ordinal, nothing deeper), materialized as
  empty `Phase` rows on apply. One new child table.
- **A full structural snapshot** mirroring Phase → Milestone/TaskList → Task → Subtask →
  Checklist (five new "template-shape" entities: `TemplatePhase`, `TemplateMilestone`,
  `TemplateTaskList`, `TemplateTask`, `TemplateSubtask`, and arguably `TemplateChecklist`), each a
  stripped-down copy of the real entity (no `status`, `assignee`, or other runtime-only fields),
  materialized as real `Phase`/`Milestone`/etc. rows on apply. This is what "project template"
  most often means in comparable tools, and would be materially more useful than V1's preset —
  but it is a subsystem on the scale of the entire P1 Work Management slice (`09`'s and `11`'s
  combined scope many times over), a deliberately separate future spec, not part of V1.

Recommending metadata-only for V1 per the explicit instruction to avoid overengineering and not
invent fields unnecessarily: it is the only option with no child tables, no new "template-shaped"
mirror entities, and a validation/authorization surface no larger than `09`–`11`.

**Everything below is V1 scope (metadata-only).** A future structural-snapshot enhancement would
require redoing §7 (API), §8 (schema), and §13 (tests) as its own spec, not extending this one.

## 4. Apply / instantiate

**[PROPOSED, following §3(a)]** Applying a template creates a new `Project` — nothing else, under
the recommended metadata-only content. This is not a new invariant: it is the *existing*
`ProjectApplicationService.createProject` invariants (organization boundary, `ProjectPriority`
required, creator becomes `OWNER` via a `ProjectMember` row created in the same transaction, the
existing `project.created` event fires) run with `request.priority()` sourced from the template
instead of the caller.

**Proposed workflow:** `ProjectTemplateApplicationService.applyTemplate(context, templateId,
ApplyTemplateRequest)`:
1. Load the active (non-archived — §5) template, organization-boundary-checked the same way
   listing/viewing is (§6).
2. Build the equivalent of a `CreateProjectRequest` — `name` from the caller-supplied
   `ApplyTemplateRequest.name()` (required; the new project's own name, not the template's),
   `description` from `ApplyTemplateRequest.description()` if supplied, else the template's own
   `description`, `priority` from `template.getDefaultPriority()`.
3. Delegate to `ProjectApplicationService.createProject` (a genuine cross-bounded-context call,
   `customization.application` → `project.application` — the same directional dependency
   `work.application.*` already has on `ProjectApplicationService`, not a new architectural
   pattern).
4. Return the created `Project`, mapped through the **existing** `ProjectResponse`/
   `ProjectMapper` — no new response shape invented for this.

**Authorization boundary:** identical to plain project creation — **[ESTABLISHED, by direct
analogy]** `ProjectApplicationService.createProject`'s own Javadoc states creation is
deliberately *not* gated through `ProjectAuthorizationService`, because a project being created
has no `projectId` yet for that interface to check against; the creator simply becomes `OWNER`.
Applying a template is creation, so it follows the identical rule: no `ProjectPermission` check
gates it, the same as plain project creation. *Who* may reach this call at all is an upstream
question — see §6, which states that boundary precisely rather than asserting organization
membership.

**[APPROVED #6] A template with a `null` `defaultPriority` cannot be applied.** `defaultPriority`
is optional on the template itself (approved Decision #4), but `Project.create` requires a
non-null `ProjectPriority`, and `ApplyTemplateRequest` accepts no priority override (step 2
above: priority always comes from `template.getDefaultPriority()`, never the caller). This gap
was not resolved when this document was first approved. The exact V1 rule:

- If `template.getDefaultPriority()` is empty, applying it is rejected with
  `BusinessRuleViolationException` (`ErrorType.BUSINESS_RULE_VIOLATION`, HTTP 422 Unprocessable
  Entity) — the same exception type and status every other business-rule rejection in this
  module already uses (e.g. `Risk.resolve()` on an already-resolved risk, `Project.archive()` on
  an already-archived project). This is the **existing** business-rule validation behavior the
  module already has, not a new error type invented for Template.
- **No arbitrary fallback priority is introduced.** The service does not silently substitute
  `MEDIUM` or any other default — doing so would fabricate a value the template never declared.
- This check happens *before* any call to `ProjectApplicationService.createProject`, so a
  template with no `defaultPriority` never reaches project creation at all; no partial or
  invalid `Project` is ever created.
- The template itself remains valid and enterable in this state (§3/§12 — `defaultPriority` is
  genuinely optional on create/update); only *applying* it is blocked until a `defaultPriority`
  is set via `PATCH`.

**Not implemented in this pass** — this section defines the workflow only, per instruction.

## 5. Template lifecycle

**[ESTABLISHED]** Standard soft-archive: `archivedAt`/`archivedBy` set together, guarded against
double-archive, excluded from active queries. No status field beyond archived/active — nothing
suggests a template needs one.

**[ESTABLISHED]** Editable after creation: yes — `name`, `description`, `defaultPriority` are
plain-edit fields via `PATCH`, the same convention every entity's non-status fields follow.

**[PROPOSED]** Archived templates **cannot** be applied — `applyTemplate` looks up the template
via the same `findByIdAndArchivedAtIsNull` convention every "active-only" lookup already uses
elsewhere, so an archived template is simply not found for this purpose (`ResourceNotFoundException`,
not a distinct "template is archived" rule). A retired template stops being offered, consistent
with "archive means no longer active" everywhere else in this codebase.

## 6. Authorization

**[APPROVED #1]** `01-SPEC.md`'s `ProjectRole` (`OWNER`/`MANAGER`/`MEMBER`/`VIEWER`) and
`ProjectAuthorizationService` are **project**-scoped by construction —
`hasPermission(userId, projectId, permission)` requires a `projectId`, sourced from a
`ProjectMember` row that only exists once a project does. There is **no organization-level
equivalent role system anywhere in this codebase** (no `OrganizationRole`, no
`OrganizationMember`). Directly reusing "OWNER/MANAGER/MEMBER/VIEWER where appropriate," as
instructed, is therefore not mechanically possible for an organization-scoped entity — this is a
finding, not an oversight, and is surfaced here rather than silently forcing a project-shaped
model onto an organization-shaped entity.

**Corrected framing — this is an upstream-contract boundary, not a membership guarantee this
module makes or verifies.** `common/context/RequestContext.java`'s own Javadoc states the
contract precisely: *"This module does not authenticate. It consumes an identity that an
upstream gateway has already authenticated... Authentication, credential handling and session
management all belong to another module."* It documents that the caller's **identity** arrives
pre-authenticated; it does **not** document that `organizationId()` being present constitutes a
verified claim that the caller is a *member* of that organization — User and Organization data
are both owned externally (`06-INTEGRATION.md`), and this module never queries either to check.
This is not a gap specific to `ProjectTemplate`: every existing entity's "organization boundary"
(`ProjectApplicationService.createProject`'s `requireOrganizationId(context)` included) already
rests on the identical trust boundary — it scopes by whatever `organizationId` the upstream
contract supplies, and has never independently reverified membership either. `ProjectTemplate`
does not weaken this; it is simply the first spec in this chain to state the boundary explicitly
instead of leaving it implicit.

**Approved:** `ProjectTemplate` enforces the organization boundary the same way `Project`
itself does — scoping every query and write by `organizationId` taken from `RequestContext`
(§2) — and adds **no additional permission check** on top of that for create, edit, archive,
list, view, or apply, for the same reason `Project` creation itself adds none (§4). Whether a
given caller was legitimately entitled to act within that `organizationId` in the first place is
governed entirely by the upstream gateway/application contract that produced the
`RequestContext` — a contract this module consumes but does not itself define, verify, or
document beyond what `RequestContext`'s own Javadoc already states. This document does not claim
otherwise, and does not invent an `OrganizationRole`/`OrganizationMember` system to manufacture a
verification this module has never performed for any entity.

**Rejected alternative:** introduce an organization-level role system
(`OrganizationRole`, `OrganizationMember`, likely mirroring `ProjectRole`'s shape) so that, say,
only organization admins can create/edit/archive templates while any caller scoped to the
organization can view/apply them. This would be a genuinely new, reusable piece of
infrastructure — larger than `ProjectTemplate` itself, and useful to other future
organization-scoped features, not just this one — and would also be the first place this module
took on verifying organization-level authorization itself, a responsibility no existing entity
takes on today. Not adopted for this spec; recorded as **its own separate future decision** if
wanted later, consistent with "avoid overengineering."

## 7. API

**[PROPOSED, extends established patterns]** No organization id in any path — every existing
endpoint resolves the organization from `RequestContext` (headers), never a path segment;
`GET /projects` itself has no `{organizationId}` in its path, and `ProjectTemplate` follows that
exact precedent rather than the "nested under project" pattern `09`–`11` used (which does not
apply here — there is no parent project).

```
POST   /api/v1/templates              (create)
GET    /api/v1/templates              (list, paginated — approved, see below)
GET    /api/v1/templates/{id}         (single item — approved, see below)
PATCH  /api/v1/templates/{id}
DELETE /api/v1/templates/{id}         (archive)
POST   /api/v1/templates/{id}/apply   (apply/instantiate — not implemented this pass)
```

**[APPROVED #2]** The single-item `GET /templates/{id}` is **included**, unlike `09`–`11`
(none of Risk/Issue/Decision/Comment/CustomField has one). Rationale: `ProjectTemplate` sits at
`Project`'s own level, not below it as a project sub-entity, and `Project` itself *does* have
`GET /projects/{id}`. This is a deliberate deviation from the majority pattern established by
the four most recent slices, not a mechanical extension of it.

**[APPROVED #5]** `GET /api/v1/templates` is **paginated**, returning `PageResponse<TemplateResponse>`
via a Spring Data `Pageable`, reusing the **existing `Project` list convention** exactly —
`ProjectController.listProjects` returns `PageResponse<ProjectSummaryResponse>` the same way.
This was not explicitly stated when this document was first approved; it follows directly from
the same rationale already used for Decision #2 (`ProjectTemplate` sits at `Project`'s own
organization-scoped level, not below it as a project sub-entity like the `work`/`ProjectCustomField`
entities, none of which paginate their plain `List<T>` responses). Default page size **20**,
sorted `createdAt` **descending**, matching `ProjectController.listProjects`'s own
`@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)` exactly — not
independently chosen for Template.

Request/response shape, reusing established conventions and existing types where possible:
`CreateTemplateRequest` (`name` required, `description` optional, `defaultPriority` optional);
`UpdateTemplateRequest` (`name`, `description`, `defaultPriority` all optional/null-means-unchanged;
`version` required); `TemplateResponse` (`id`, `organizationId`, `name`, `description`,
`defaultPriority`, `archived`, `createdAt`, `updatedAt`, `version`); `ApplyTemplateRequest`
(`name` required — the new project's name; `description` optional override); the apply
endpoint's response is the **existing** `ProjectResponse` (`project.api.dto.ProjectResponse`),
not a new type — applying a template produces a real `Project`, so it is represented exactly as
one everywhere else already is.

## 8. Database

**[APPROVED shape, reflecting §3(a) and Decisions #3/#4]** Described here only — no migration
file is created by this document. A future `V10__create_project_templates.sql` would follow this
shape:

```sql
CREATE TABLE project_templates (
    id                 uuid         NOT NULL,
    organization_id    uuid         NOT NULL,
    name               varchar(200) NOT NULL,
    description        text,
    default_priority   varchar(20),
    archived_at        timestamptz,
    archived_by        uuid,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_project_templates PRIMARY KEY (id),
    CONSTRAINT ck_project_templates_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_project_templates_default_priority
        CHECK (default_priority IS NULL OR default_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_project_templates_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
    -- No FK on organization_id: opaque reference, same as projects.organization_id.
);

CREATE INDEX idx_project_templates_organization ON project_templates (organization_id);
CREATE INDEX idx_project_templates_active ON project_templates (organization_id) WHERE archived_at IS NULL;

-- Approved (Decision #3): active-name uniqueness per organization, as a partial unique index
-- (the same corrected form 11-CUSTOMFIELD-SPEC.md §15 used) — NOT a table-level UNIQUE
-- constraint, which cannot carry a WHERE predicate in PostgreSQL.
CREATE UNIQUE INDEX uq_project_templates_org_name_active
    ON project_templates (organization_id, name)
    WHERE archived_at IS NULL;
```

**One table is sufficient for V1's decided metadata-only scope (§3(a)).** If a future
enhancement pursues §3(b) or §3(c) instead (out of scope for V1), one or several child tables
would become necessary (§3 describes their approximate shape), and this section would need to be
redone as part of that separate spec, not merely extended.

## 9. Events / outbox / audit

**[PROPOSED, recommended: no `template.*` events]** `05-EVENTS.md` has no `## Template`/
`## ProjectTemplate` section — the same gap Issue/Decision/Comment/CustomField all had.
Recommending the same resolution: no domain events, no `WorkEventRecorder` (or equivalent)
integration, no outbox messages, no `ProjectActivity` rows *for the template record itself*.

**This is distinct from applying a template**, which is not exempt from events at all: because
`applyTemplate` delegates to the real `ProjectApplicationService.createProject` (§4), the
resulting `Project` goes through that service's existing, unmodified event pipeline —
`project.created` fires exactly as it does for any other project creation. No new event is
invented for "created via template"; the existing one is not distinguished by its origin.

## 10. Integration

**[ESTABLISHED]** `organization_id` is an opaque reference with no foreign key, identical to
`projects.organization_id` (`06-INTEGRATION.md` — Organization is owned by another module). No
`User`/`Organization` entity or table is created. No "created by" field is proposed either — like
`Risk`/`Issue`/`ProjectCustomField`, a template stores no creator-attribution field of its own
(only `Project`, `Decision`, and `ProjectComment` store an explicit actor reference, each for a
reason specific to that entity); adding one here would be an unrequested extra field.

## 11. AI / automation

**[NONE, deferred]** `07-AI.md` does not mention templates anywhere. No AI-suggested template
content, no auto-generated templates, no AI-driven "recommend a template for this project" — all
explicitly out of scope now, per standing project instructions independent of documentation.

## 12. Validation

**[PROPOSED]** `name`: not blank, ≤200 characters — same shape as every entity. `description`:
optional, no length cap (matches every other `description` field). `defaultPriority`: optional;
if supplied, must be a valid `ProjectPriority` (enforced by enum binding, the same as every other
enum-typed field — no separate parse step, unlike `ProjectCustomField.value`, because this is a
real enum column, not a text column standing in for one). `ApplyTemplateRequest.name`: required,
not blank, ≤200 — the same constraint `CreateProjectRequest.name` already carries, since it
becomes the new project's name.

## 13. Testing

**[ESTABLISHED shape, plus apply-specific coverage]** The same four-layer suite, extended for the
apply workflow:

- `ProjectTemplateTest` (domain unit): creation, blank-name rejection, plain edits, archive guard.
- `ProjectTemplateApplicationServiceTest` (Mockito): create, list/view (organization-boundary
  denial across organizations), update (stale-version, not-found), archive, **and**: `applyTemplate`
  delegates to `ProjectApplicationService.createProject` with the template's `defaultPriority`;
  applying an archived template reports not-found; applying across an organization boundary is
  denied the same way every other cross-organization access is.
- `ProjectTemplateControllerTest` (`@WebMvcTest`): all six endpoints (including `apply`),
  validation-rejection cases.
- `ProjectTemplatePersistenceTest` (`@DataJpaTest`, **real PostgreSQL**,
  `@EnabledIfEnvironmentVariable`, confirmed actually executing, not skipped, the same discipline
  every prior persistence test established): round-trip, `default_priority` `CHECK` rejection,
  archive round-trip, active-listing exclusion, and the partial-unique-index rejection on a
  duplicate active name within the same organization (Decision #3).

## 14. Package placement

**[DECIDED by pre-existing architecture, not newly proposed]** `src/main/java/com/projectmodule/
customization/package-info.java` — part of the original foundation commit, predating every slice
in this conversation — explicitly states: *"Bounded context: tenant-level customization.
ProjectCustomField and ProjectTemplate."* `ProjectTemplate` therefore belongs under
`com.projectmodule.customization.{domain,application,api,infrastructure}`, mirroring the exact
sub-package layering `work.*` already uses (`domain` → `application` → `api`, `infrastructure`
for persistence).

**Recorded inconsistency, not corrected here (per instruction):** `ProjectCustomField` was
implemented under `com.projectmodule.work.*` in the prior slice, not
`com.projectmodule.customization.*` as this pre-existing package-info names it. This document
does not move it. Whether to relocate `ProjectCustomField` into `customization` later — and
whether that is worth the churn for a shipped, tested entity — is recorded here as **its own
separate future decision**, deliberately not bundled into `ProjectTemplate`'s implementation.

## Decided (no longer open)

- **Content / relationships, V1 scope (§3).** Metadata-only (`name`, `description`,
  `defaultPriority`; no child tables) — V1's purpose is a reusable **project-default preset**,
  not a structural project blueprint. A shallow phase-name snapshot and a full structural
  snapshot mirroring Phase→Milestone/TaskList→Task→Subtask→Checklist (five-plus new entities — a
  subsystem on the scale of the entire P1 Work Management slice) are both **explicitly OUT OF
  SCOPE for V1**, recorded as a possible **future enhancement** via a separate future spec, not
  an option still being weighed here.

## Open decisions — resolved via approval

All six were presented and **confirmed, each choosing the recommended option**:

1. **Authorization model (§6). APPROVED: no additional permission system.** `ProjectTemplate`
   scopes by `organizationId` from `RequestContext` and adds no additional permission check
   beyond that, mirroring `Project` creation's own rule — this module does not itself verify
   organization membership for `ProjectTemplate` any more than it does for any other entity; that
   trust boundary belongs to the upstream gateway/application contract that supplies
   `RequestContext`. Rejected alternative: introduce a new organization-level role system
   (`OrganizationRole`/`OrganizationMember`).
2. **Single-item `GET /templates/{id}` (§7). APPROVED: included** (mirrors `Project`, which sits
   at the same organization level). Rejected alternative: omit it, mirroring Risk/Issue/Decision/
   Comment/CustomField instead.
3. **Active-name uniqueness per organization (§8). APPROVED: enforced via a partial unique
   index** (`organization_id`, `name`, active rows only — `CREATE UNIQUE INDEX ... WHERE
   archived_at IS NULL`, not a table-level `UNIQUE` constraint, which cannot carry a `WHERE`
   predicate in PostgreSQL), the same "duplicate protection" reasoning
   `11-CUSTOMFIELD-SPEC.md` Decision #7 used. Rejected alternative: allow duplicate template
   names within an organization.
4. **`defaultPriority` field (§3/§12). APPROVED: included, optional `ProjectPriority`.**
   Rejected alternative: omit it entirely.
5. **`GET /templates` pagination (§7). APPROVED: paginated**, returning
   `PageResponse<TemplateResponse>` via `Pageable`, reusing `ProjectController.listProjects`'s
   own convention exactly — default page size 20, sorted `createdAt` descending. Identified as a
   gap during implementation review (the approved spec had not explicitly stated this); resolved
   by extending the same organization-level reasoning already used for Decision #2, not by a new
   independent choice.
6. **Null `defaultPriority` on apply (§4). APPROVED: rejected, no fallback.** A template with no
   `defaultPriority` cannot be applied — `applyTemplate` throws `BusinessRuleViolationException`
   (HTTP 422), the module's existing business-rule-rejection behavior, before ever calling
   `ProjectApplicationService.createProject`. No arbitrary fallback priority (e.g. `MEDIUM`) is
   substituted. Also identified as a gap during implementation review: approved Decision #4 makes
   `defaultPriority` optional on the template, but `Project.create` requires a non-null priority
   and `ApplyTemplateRequest` carries no override — this document had not stated what happens in
   that combination.

Every other section in this document already reflected these outcomes (each was written as the
approved option) — sections 1, 2, 4, 5, 9, 10, 11, 12, 13, 14, and the V1 scope decision, require
no further change as a result of this approval.

## Implementation-readiness

**Implementation-ready.** All open decisions are resolved (above), including the V1 scope
decision (content/relationships, §3) and the two checkpoint-review additions (#5 pagination, #6
null-`defaultPriority`-on-apply). Every remaining section is a mechanical application of existing
convention — extended one level up to the organization scope where §2 requires it — the same as
every prior slice. This specification now matches the implementation delivered in the prior
pass; no further Java code, SQL migration, or test changes are implied by this update — this
file is the only change made in this pass.
