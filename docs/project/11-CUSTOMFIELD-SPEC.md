# ProjectCustomField — Approved Specification

## Status of this document

**Approved.** All nine open decisions below were presented and confirmed by the project owner,
each choosing the recommended option. Following the same process `09-DECISION-SPEC.md` and
`10-COMMENT-SPEC.md` established: `01-SPEC.md:33` and `03-DATABASE.md:19` document
`ProjectCustomField` only as a bare entity name — no fields, value types, statuses, API, or
events, and unlike Risk/Issue, it is not even listed anywhere in `08-ROADMAP.md`. It is now this
module's specification for ProjectCustomField, to be followed exactly the same way `01-SPEC.md`
through `10-COMMENT-SPEC.md` are.

This entity had a **larger open-decision surface than Decision or Comment**: those were both
recognizable, narrowly-scoped record types (a decision log entry, a remark) with an obvious
minimal shape. "Custom field" implies a small type system — what kinds of values, how they are
declared, how they are stored — that has no anchor anywhere in this codebase's conventions to
derive from even provisionally. Where Decision had 4 open decisions and Comment had 6, this
document had 9. That was a direct, honest consequence of how little the source documentation
constrains this entity, not scope creep on this proposal's part.

Tags used throughout, identical to `09`/`10`:

- **[ESTABLISHED]** — a direct, mechanical application of a convention already in force for every
  comparable entity (Phase, Task, Risk, Issue, Decision, ProjectComment). Not a new design choice.
- **[PROPOSED]** — new, invented for CustomField because nothing in the docs addresses it, but
  with one clear best answer given existing conventions (low controversy).
- **[APPROVED]** — was an **[OPEN DECISION]** below, now confirmed by the project owner. The
  surrounding text is left as originally proposed (it already described the approved,
  recommended option); see the final section for the confirmed outcome of each.

## 1. Purpose and scope

**[PROPOSED]** A `ProjectCustomField` lets a project record a small piece of project-specific
data that has no dedicated built-in field — e.g. a "Client Code" or "Budget Tier" a particular
organization wants tracked, without the Project Module needing a schema change for it. This
framing is not stated anywhere; it is the ordinary meaning of "custom field" in comparable
project-management tools, offered as the basis for every choice below.

## 2. What a ProjectCustomField represents — definition, value, or both

**[APPROVED #1 — the central architectural fork; a single combined entity]**
`01-SPEC.md`/`03-DATABASE.md` name exactly **one** entity, `ProjectCustomField` — not two (they
do list multiple related entities elsewhere, e.g. `AIRecommendation`/`AIAction`/`AIApproval`, so
a deliberate one-entity naming here is meaningful evidence). Approved: **one row = one named,
typed value, scoped to one project** — a field's "definition" (its name and value type) and its
current value live in the same row. There is no separate, reusable field-*definition* registry
that many projects reference; each project's custom fields are independent.

**Rejected alternative:** split into two entities — `ProjectCustomFieldDefinition` (name, type,
declared once, possibly organization-wide) and `ProjectCustomFieldValue` (one per project per
definition). This would have been a materially larger feature (a shared-schema registry,
definitions outliving any single project, reuse across projects) with no textual basis for it in
`01-SPEC.md`/`03-DATABASE.md`.

**Everything below assumes the approved combined-entity model.**

## 3. Which project entities it can attach to

**[APPROVED #2: Project only]** Same reasoning already applied to
`ProjectComment` (`10-COMMENT-SPEC.md` §2): the entity is named `ProjectCustomField`, not
`CustomField`, and no document ever generalizes it to Task/Risk/Issue/Decision.

**Alternative:** a polymorphic target (`target_type` + `target_id`), letting Tasks, Risks, etc.
also carry custom fields. Rejected as the recommendation for the same reasons `10-COMMENT-SPEC.md`
gave: nothing asks for it, and it meaningfully expands authorization/validation surface with no
documented requirement.

## 4. Supported value types

**[APPROVED #3]** Nothing documents what kinds of values a custom field can hold. Approved: a
**minimal four-type system**: `TEXT`, `NUMBER`, `DATE`, `BOOLEAN` — the smallest
set that covers the ordinary "extra project attribute" use case (a code, a budget figure, a
deadline, a yes/no flag), stored as a plain enum the same way `TaskStatus`/`RiskStatus`/
`ProjectPriority` already are (`@Enumerated(EnumType.STRING)`, a `varchar` + `CHECK`).

**Alternatives:**
(a) **`TEXT` only** — simplest possible, avoids inventing any type system at all; every value is
a string and any structure (numeric, date) is the caller's problem. Lowest invention, but also
the least useful "custom field" in practice.
(b) **The four types above, plus `SELECT`** (a single choice from a fixed, per-field list of
options) — closer to what most real custom-field systems offer, but requires a second concept
(an options list per field) with no precedent anywhere in this codebase, a materially bigger
addition than the other three.

## 5. Field definition structure

**[PROPOSED, following §2's approved model]** Each row carries: `name` (the field's label,
e.g. "Client Code"), `valueType` (§4), and `value` (§6). No separate "description" of what the
field means, no display ordering, no grouping — see Non-goals.

## 6. Value storage / representation

**[APPROVED #4]** How to store a value whose shape depends on `valueType`, in one row.
Approved: a **single `value` column of type `text`**, always stored as its string
representation and parsed/validated against the declared `valueType` at the application layer
(e.g. `NUMBER` must parse as a number, `DATE` as an ISO date, `BOOLEAN` as `true`/`false`) before
being accepted. This mirrors how every existing enum-backed column in this codebase is a plain
`varchar`, just applied to a value whose *meaning* varies rather than a closed enum.

**Alternatives:**
(a) **One nullable column per type** (`text_value`, `number_value`, `date_value`,
`boolean_value`), only the one matching `valueType` ever populated. Gives the database real
per-type storage and comparison, at the cost of four sparse nullable columns and a
column-selection branch everywhere the value is read or written.
(b) **A `jsonb` value column.** `jsonb` already exists in this codebase (`outbox.payload`,
`project_activity.payload` in `V1__create_project_foundation.sql`) — but only for opaque,
never-queried event payloads, not for typed, directly-compared domain data. Using it here would
be a new application of an existing column type, not a fully established convention, and would
mean losing SQL-level `CHECK` validation of the value's shape entirely.

## 7. Required vs optional behavior

**[APPROVED #6, follows from §2]** Under the approved combined-entity model, "required/optional"
as a schema-level concept (a *definition* declaring itself mandatory, checked against something)
does not apply — there is no separate definition outliving any given row, so a `ProjectCustomField`
either exists with a value or does not exist at all. `value` itself is required and not blank at
the row level (§8), which is a different, simpler thing than a required-field-across-a-project
rule. (The split-model alternative, under which a `ProjectCustomFieldDefinition` could carry a
meaningful `isRequired` flag, was not chosen — see Decision #1.)

## 8. Validation rules

**[PROPOSED]** `name`: not blank, ≤200 characters — the same shape and `ValidationException` as
every other entity's name field. `value`: not blank (a blank custom field has no reason to
exist under the combined model — an unwanted one is archived, not left empty). `value`, once
present, must additionally satisfy its declared `valueType` (§4/§6): a `NUMBER` field's value
must parse as a number, a `DATE` field's value must parse as an ISO-8601 date, a `BOOLEAN`
field's value must be exactly `true` or `false`; a `TEXT` field's value has no further
constraint. Failing this parse raises `ValidationException`, the same as every other rule
violation.

## 9. Create / update / archive lifecycle

**[PROPOSED core shape, APPROVED #5 on type mutability]** Create: `name`, `valueType`,
`value` all supplied together. Update: `name` and `value` are editable via `PATCH` (plain field
edits, no event — see §16). **Approved: `valueType` is immutable after creation** — changing
it out from under an existing `value` (e.g. `TEXT` → `NUMBER` when the stored value is
"unspecified") would silently invalidate that value, so retyping a field means archiving it and
creating a new one. (The alternative — allowing `valueType` to change, with re-validation or
clearing of the existing value — was not chosen.) Archive: the same soft-archive convention every
entity has, no event.

## 10. Authorization

**[ESTABLISHED]** Unlike `ProjectComment` (which needed an author-identity concept because a
comment is a personal remark), a custom field is project configuration data — structurally like
Risk/Issue/Decision, not like a comment — so this is a fully mechanical application of the
existing pattern, not an open question: `ProjectAuthorizationService`, reusing the existing five
permissions, no new one added.

- Create, update, archive → `ProjectPermission.EDIT_PROJECT`
- List → `ProjectPermission.VIEW_PROJECT`

## 11. Organization/project boundary rules

**[ESTABLISHED]** Identical to every entity: `projectId` is a plain column (no JPA association);
the organization boundary is enforced via
`ProjectApplicationService.findActiveProjectInCallerOrganization` before every operation.

## 12. API endpoints

**[PROPOSED, extends the established pattern]** `04-API.md` defines no CustomField endpoints —
the same gap Risk/Issue/Decision/Comment had. Extending the identical "collection nested under
project, item flat" pattern:

```
POST   /api/v1/projects/{projectId}/custom-fields
GET    /api/v1/projects/{projectId}/custom-fields
PATCH  /api/v1/custom-fields/{id}
DELETE /api/v1/custom-fields/{id}
```

No single-item `GET /custom-fields/{id}`, matching every entity except Task.

Request/response shape: `CreateCustomFieldRequest` (`name`, `valueType`, `value`, all required);
`UpdateCustomFieldRequest` (`name`, `value` optional/null-means-unchanged — not `valueType`, per
§9; `version` required); `CustomFieldResponse` (`id`, `projectId`, `name`, `valueType`, `value`,
`archived`, `createdAt`, `updatedAt`, `version`).

## 13. Pagination / list behavior

**[APPROVED #9: unbounded list, not paginated]** Structurally, a project's
custom fields are a small, bounded set of configuration attributes — closer in growth pattern to
Risks/Issues/Decisions (plain `List<XResponse>`, no pagination) than to Comments (an
open-ended, potentially large discussion log, which is why `10-COMMENT-SPEC.md` recommended
reusing the `PageResponse`/`Pageable` convention). Approved: the simpler unbounded list,
ordered `createdAt ASC`, matching Risk/Issue/Decision's convention. (The paginated alternative,
reusing `PageResponse`/`Pageable` as Comment does, was not chosen.)

## 14. Optimistic locking

**[ESTABLISHED]** `version` required in `UpdateCustomFieldRequest`; the application service
compares it to the loaded entity's version and throws `ConflictException` on mismatch, backed by
Hibernate's `@Version` flush-time check — identical to every other `update*` use case.

## 15. Database schema and migration

**[APPROVED shape, reflecting Decisions #3/#4/#7]** Described here only — **no migration
file is created by this document**, per instruction. A future `V9__create_project_custom_fields.sql`
would follow this shape:

```sql
CREATE TABLE project_custom_fields (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    value_type       varchar(20)  NOT NULL,  -- Decision #3: 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN'
    value            text         NOT NULL,  -- Decision #4: interpreted per value_type at the application layer
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_project_custom_fields PRIMARY KEY (id),
    CONSTRAINT fk_project_custom_fields_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_custom_fields_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_project_custom_fields_value_not_blank
        CHECK (length(btrim(value)) > 0),
    CONSTRAINT ck_project_custom_fields_value_type
        CHECK (value_type IN ('TEXT', 'NUMBER', 'DATE', 'BOOLEAN')),
    CONSTRAINT ck_project_custom_fields_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

CREATE INDEX idx_project_custom_fields_project ON project_custom_fields (project_id);
CREATE INDEX idx_project_custom_fields_active ON project_custom_fields (project_id) WHERE archived_at IS NULL;

-- Decision #7: active-row uniqueness cannot be expressed as a table-level UNIQUE constraint in
-- PostgreSQL (a table CONSTRAINT ... UNIQUE accepts no WHERE predicate) — it requires a partial
-- unique INDEX instead. This is the corrected form; the original proposal's
-- "UNIQUE (project_id, name) WHERE archived_at IS NULL" table-constraint line was invalid syntax.
CREATE UNIQUE INDEX uq_project_custom_fields_project_name_active
    ON project_custom_fields (project_id, name)
    WHERE archived_at IS NULL;
```

**[APPROVED #7]** The partial unique index above prevents two active custom fields on the same
project sharing a name, the same "duplicate protection" reasoning `Dependency`'s unique-pair
constraint already uses elsewhere in this codebase. (The alternative — allowing duplicate names —
was not chosen.)

## 16. Events / outbox / audit behavior

**[APPROVED #8: none]** `05-EVENTS.md` has no `## CustomField`/
`## ProjectCustomField` section — zero events documented, the same gap Issue/Decision/Comment
all had. Approved: the same resolution again — no domain events, no `WorkEventRecorder`
integration, no outbox messages, no `ProjectActivity` rows.

## 17. Integration implications

**[ESTABLISHED]** None. `06-INTEGRATION.md` — "Project Module Owns: all project-specific
business logic and data." A custom field's `value` never references another module (unlike
`Task.assigneeId`/`Decision.decidedBy`/`ProjectComment.authorId`, there is no opaque external
reference here at all — every value type in §4 is a plain scalar).

## 18. AI implications

**[NONE DOCUMENTED]** `07-AI.md` does not mention custom fields anywhere. Per standing project
instructions, no AI behavior is in scope regardless of documentation — no AI-suggested field
values, no auto-detected value types, no AI-generated field names. Noted explicitly because a
"custom field" is exactly the kind of structured data an AI Recommendation feature (P4) might
plausibly want to populate later; that is a separate, future capability layered on top of a
working CustomField CRUD, not a prerequisite for it.

## 19. Required tests

**[ESTABLISHED shape]** The same four-layer suite every entity has:

- `ProjectCustomFieldTest` (domain unit): creation, blank-name/blank-value rejection,
  per-`valueType` value validation (a `NUMBER` field rejects a non-numeric value, etc.), edit,
  archive guard.
- `ProjectCustomFieldApplicationServiceTest` (Mockito): create + `EDIT_PROJECT` denial, list,
  update (stale-version rejection, not-found), archive, and duplicate-name-within-project
  rejection (Decision #7).
- `ProjectCustomFieldControllerTest` (`@WebMvcTest`): all four endpoints, validation-rejection
  cases (blank name/value, invalid value for the declared type).
- `ProjectCustomFieldPersistenceTest` (`@DataJpaTest`, **real PostgreSQL**,
  `@EnabledIfEnvironmentVariable`, confirmed actually executing, not skipped, the same discipline
  `DecisionPersistenceTest`/`ProjectCommentPersistenceTest` established): round-trip, FK
  enforcement, blank-value/blank-name `CHECK` rejection, `value_type` `CHECK` rejection, the
  partial-unique-index rejection on a duplicate active name (Decision #7), archive round-trip,
  active-listing exclusion.

## 20. Explicit non-goals

To keep this narrowly scoped:

- No cross-project shared field-definition registry or reuse (§2) — every project's custom
  fields are independent rows, not references to an organization-wide schema.
- No custom fields on Task/Risk/Issue/Decision (§3) — Project only.
- No `SELECT`/multi-choice value type, or any type beyond the four approved in §4.
- No field grouping, sectioning, or display ordering.
- No field-level permissions distinct from the project's own `EDIT_PROJECT`/`VIEW_PROJECT`.
- No formula/computed fields, no cross-field validation.
- No AI-suggested types, values, or names (§18).
- No history of previous values — only the current `value` is stored, the same convention
  `10-COMMENT-SPEC.md` §23 applied to comment edits.

## Open decisions — resolved via approval

All nine were presented and **confirmed, each choosing the recommended option**:

1. **Definition-vs-value model (§2). APPROVED: one combined entity** (definition + current
   value in one row, per-project, not shared). Rejected alternative: split into a reusable
   `ProjectCustomFieldDefinition` + per-project `ProjectCustomFieldValue`.
2. **Target scope (§3). APPROVED: Project only.** Rejected alternative: polymorphic target
   (Task/Risk/Issue/Decision custom fields too).
3. **Supported value types (§4). APPROVED: `TEXT`/`NUMBER`/`DATE`/`BOOLEAN`.** Rejected
   alternatives: (a) `TEXT` only; (b) the four types plus `SELECT`.
4. **Value storage representation (§6). APPROVED: single `text` column**, parsed per
   `valueType` at the application layer. Rejected alternatives: (a) one nullable column per
   type; (b) a `jsonb` value column.
5. **Value-type mutability (§9). APPROVED: `valueType` immutable after creation.** Rejected
   alternative: mutable, with re-validation (or clearing) of the existing value.
6. **Required/optional semantics (§7). APPROVED: void under the combined model** (Decision
   #1) — a field either exists with a value or doesn't exist.
7. **Uniqueness of name per project (§15). APPROVED: enforce via a partial unique index**
   (`project_id`, `name`, active rows only — `CREATE UNIQUE INDEX ... WHERE archived_at IS NULL`,
   not a table-level `UNIQUE` constraint, which cannot carry a `WHERE` predicate in PostgreSQL).
   Rejected alternative: allow duplicate names.
8. **Events/outbox (§16). APPROVED: none** (mirrors Issue/Decision/Comment). Rejected
   alternative: invent `custom_field.created`/`updated` events.
9. **Pagination (§13). APPROVED: unbounded list** (mirrors Risk/Issue/Decision). Rejected
   alternative: paginated `PageResponse`/`Pageable` (mirrors Comment).

Every other section in this document already reflected these outcomes (each was written as the
approved option) — sections 1, 5, 8, 10, 11, 12, 14, 17, 18, 19, 20 require no further change as
a result of this approval.

## Implementation-readiness

**Implementation-ready.** All nine open decisions are resolved (above). Every remaining section
is a mechanical application of existing convention, the same as every prior slice. No Java code,
SQL migration, or tests have been written yet — this file is the only change made in this pass.
