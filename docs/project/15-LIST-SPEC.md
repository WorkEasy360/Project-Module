# List (P2) — Specification

## Status of this document

**Resolved and implementation-ready**, following the same single-pass process
`14-FILTERS-SPEC.md` used: every ambiguity below is resolved directly, choosing the smallest,
most conservative design, rather than left open for a later approval round.

`08-ROADMAP.md:24` lists `List` under P2 — Views as a single bullet. No other document mentions
it. `14-FILTERS-SPEC.md` §7 explicitly excluded sorting/pagination changes ("No pagination/sorting
changes... out of scope for this slice"), which is the strongest existing signal for what "List"
(the next P2 bullet) is meant to add on top of Filters.

Tags: **[ESTABLISHED]** = a direct, mechanical application of an existing convention.
**[RESOLVED — conservative]** = genuinely undefined; resolved by choosing the smallest,
least-invasive option.

## Purpose

**[RESOLVED — conservative]** List is: the existing filtered project-scoped list endpoints
(Task, Risk, Issue, Decision — the same four entities `13-SEARCH-SPEC.md`/`14-FILTERS-SPEC.md`
already scoped themselves to) gain an optional, caller-selected **sort order** on top of the
filtering `14-FILTERS-SPEC.md` already added. It is *not* pagination: `PageResponse<T>` already
exists and is wired into four other controllers (`ProjectController`, `TemplateController`,
`CommentController`, `SearchController`), but those endpoints were paginated from their first
implementation. Task/Risk/Issue/Decision's list endpoints were published unpaginated and
`14-FILTERS-SPEC.md` explicitly refused to change their response type for exactly this reason
("changing to `PageResponse` would modify an existing API contract, off-limits") — that
constraint is unchanged here. Adding a sort parameter is response-type-compatible (same
`List<T>`, only element order changes) and, when omitted, produces the exact same order as
today, so it is a purely additive change to the same endpoints and the same filtered method
overloads Filters just introduced.

## 1. Scope

**[RESOLVED — conservative]** Task, Risk, Issue, Decision — identical entity boundary to
Search/Filters, for the same reason: keep these three closely related P2 capabilities
consistent rather than introducing a fourth, different notion of "which entities are in scope."

Out of scope: pagination (see Purpose), any entity outside the four above, sorting by fields
that don't exist on the entity (e.g. no `assigneeId` sort for Risk/Issue/Decision — they have no
such field).

## 2. Sortable fields (per entity, exhaustive)

**[RESOLVED — conservative]** Only fields that already exist on the entity and are meaningful to
order by. Declared as a dedicated enum per entity (reusing the exact "invalid enum value → 400
`validation-failed` via `MethodArgumentTypeMismatchException`" mechanism `14-FILTERS-SPEC.md`
already added to `GlobalExceptionHandler` — no new error-handling code needed):

| Entity   | Enum              | Values                                    | Default   |
|----------|-------------------|--------------------------------------------|-----------|
| Task     | `TaskSortField`     | `CREATED_AT`, `DUE_DATE`, `NAME`, `STATUS`  | `CREATED_AT` |
| Risk     | `RiskSortField`     | `CREATED_AT`, `NAME`, `PRIORITY`, `STATUS`  | `CREATED_AT` |
| Issue    | `IssueSortField`    | `CREATED_AT`, `NAME`, `PRIORITY`            | `CREATED_AT` |
| Decision | `DecisionSortField` | `CREATED_AT`, `NAME`                        | `CREATED_AT` |

Direction: a single shared `com.projectmodule.common.api.SortDirection` enum {`ASC`, `DESC`},
default `ASC` — matches every existing `ORDER BY ... ASC` in this codebase.

When `sortBy`/`sortDir` are omitted, behavior is byte-for-byte identical to the current
(pre-List) behavior: `ORDER BY createdAt ASC`. This is what makes the change purely additive.

## 3. API

**[ESTABLISHED]** No new endpoint. Two new optional query parameters, `sortBy` and `sortDir`,
added to the same `GET` list endpoints Filters already extended:
`GET /api/v1/projects/{projectId}/tasks`, `/risks`, `/issues`, `/decisions`. Response type
unchanged: `List<TaskResponse>` / `List<RiskResponse>` / `List<IssueResponse>` /
`List<DecisionResponse>`.

## 4. Implementation shape

**[ESTABLISHED]** The filtered repository method Filters added for each entity
(`findByFilters(...)`) gains one trailing `org.springframework.data.domain.Sort` parameter —
Spring Data JPA applies a trailing `Sort` argument to a `@Query`-annotated method automatically,
without needing dynamic SQL, a `Specification`, or any new infrastructure. The corresponding
filtered application-service overload (added by Filters) gains two trailing parameters
(`XSortField sortBy`, `SortDirection sortDir`, both defaulted by the controller when the request
omits them) and builds the `Sort` object by mapping the enum constant to the entity's actual
JPA property name (e.g. `TaskSortField.DUE_DATE` → `"dueDate"`).

The pre-existing, unfiltered 2-arg `listX(context, projectId)` overload is untouched, exactly as
Filters left it untouched.

## 5. Authorization

**[ESTABLISHED]** Unchanged: the same `VIEW_PROJECT` check already on every list method.

## 6. Testing

**[ESTABLISHED]** Same layered pattern as Filters: application-service tests (sort applied,
default sort when omitted, invalid combinations impossible by construction since the sort field
is a closed enum), controller tests (binds `sortBy`/`sortDir`, invalid enum value → 400
`validation-failed` reusing the existing handler test pattern), persistence tests against real
PostgreSQL proving the `Sort` parameter actually changes row order for each sortable field.

## 7. Out of scope

Pagination (see Purpose), multi-field sort, sorting on custom fields, sorting on any entity
outside Task/Risk/Issue/Decision, changing the unfiltered/unsorted 2-arg overload or its
callers.
