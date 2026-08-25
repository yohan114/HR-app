# ADR 0006 — Field-level permissions: split defaults, server-side projection

- **Status:** Accepted
- **Date:** 2026-08-24
- **Phase:** 1 (tasks P1-BE-15, P1-BE-21)

## Context

Row-level security ([ADR 0002](0002-multi-tenancy-rls.md)) answers "whose data?". Role-based
permissions answer "may this user open an employee record?". Neither answers the question that
actually decides whether an HR system is deployable inside an organisation: **which fields of that
record?**

The distinction is not academic. A manager legitimately needs their team's work contact details
and reporting lines. They must not see salary grade, national identity number, home address or
date of birth. An HR administrator maintaining records may need to correct a misspelled name
without being shown passport numbers — because in most organisations "maintains employee records"
and "handles identity documents" are different jobs held by different people, and a system that
cannot separate them forces the customer to over-grant.

The mechanism has to work for fields the codebase has never heard of, too: tenants add their own
fields via `field_definition` (`V7__custom_fields.sql`), and a tenant-defined "Disciplinary notes"
field needs the same controls as a built-in one.

`field_permission` (role × entity × field → HIDDEN/MASKED/READ/WRITE) was created in
`V2__identity.sql`. This ADR is about the part that migration deferred: what happens for the
overwhelming majority of fields that have **no row at all**, and where enforcement lives.

## Decision

### 1. The default is split three ways, not uniform

| | reading | writing |
|---|---|---|
| ordinary field | `READ` | only with the entity's manage permission, or self-service on an allow-listed field |
| field in `ALWAYS_SENSITIVE` | `HIDDEN` — `READ` on your own record | never, without an explicit grant |

Two properties follow, and both are deliberate:

- **Write is never the default.** Not for ordinary fields, not even on your own record.
- **The manage permission does not confer sight of sensitive fields.** `employee.manage` makes you
  able to edit a profile; it does not make you able to read a national identity number.

### 2. `ALWAYS_SENSITIVE` lives in code, not in configuration

`FieldPermissionResolver.ALWAYS_SENSITIVE` is a compile-time map. A tenant cannot empty it.

### 3. Self-service is authorised by ownership, not by a permission grant

`FieldAccessContext.subjectIsSelf` is set by comparing the caller's `employee_id` claim to the
record. A short allow-list (`SELF_WRITABLE`) covers contact details, preferred name and photo.

### 4. Enforcement is a projection, not a serialisation filter

There is no path from an `Employee` entity to a JSON response except `EmployeeProjection.project`,
which takes a `FieldAccessContext` and cannot produce output without consulting it. No
`@JsonFilter`, no annotation, no interceptor.

### 5. Hidden means absent; masking happens server-side

A field the caller may not see is **omitted from the payload**, not set to null. A masked field is
redacted before serialisation; the true value never leaves the process.

## Rationale

**Why not deny-by-default.** It is the reflexive security answer and it is unusable here. Every
field of every entity would need explicit configuration before anyone could see anything. The
predictable outcome is not a carefully configured tenant — it is an administrator, on day one,
granting everything to everyone so the product works at all, and never revisiting it. A default
that reliably produces a worse configured system than a weaker default is not the safer choice.

**Why not allow-by-default.** Unsafe twice over, and the second way is the one that gets missed.
The obvious failure is that a newly added sensitive column is exposed to everyone until somebody
remembers to restrict it — silent, and with no expiry. The less obvious one surfaced during
implementation: with `WRITE` as the ordinary default, an employee holding no permissions at all
could `PATCH` their own `joinDate`, which feeds gratuity and service-based leave accrual. Reading
a colleague's department is harmless; writing your own tenure is not. Read and write cannot share
a default.

**Why the manage permission does not imply sight of sensitive fields.** If it did, the separation
of "maintains records" from "handles identity documents" would be unavailable to customers no
matter how they configured roles, because the default would already have granted both. Defaults
that foreclose a legitimate configuration are worse than restrictive defaults that can be opened
up: opening up is a one-click grant, and discovering your HR team has been able to read everyone's
passport numbers for a year is not recoverable.

**Why `ALWAYS_SENSITIVE` is in code.** Two reasons. A tenant administrator should not be able to
make their own employees' identity numbers world-readable by clearing a row — the people harmed by
that are not the people who would make the mistake. And putting it in code means adding a
sensitive column and classifying it are the *same commit*, made by the person who still has the
context to decide. Configuration would let the classification lag the column indefinitely, and the
lag is exactly the exposure window.

**Why a projection rather than a Jackson filter.** A `@JsonFilter` binds to a serialisation path.
A future endpoint that returns the entity by another route — nested inside another object, inside
a list wrapper, in a domain event payload, in a CSV export — silently skips it. The leak is
invisible in code review, because a *missing annotation looks like nothing at all*. With a
projection there is no unfiltered variant to reach for: the only function that turns an employee
into a payload requires the caller's context as an argument. Forgetting to apply the filter stops
being a mistake it is possible to make.

The cost is an explicit accessor per field, which must be extended when a column is added. That is
a real maintenance burden and it is the point: adding a column forces a decision about who may see
it, at the moment the person adding it can make that decision. A reflective projection would expose
the new column to everyone by default and nobody would notice.

**Why hidden means absent rather than null.** A null tells the caller the field exists and they are
not allowed it. That is itself information, and it invites clients to render a disabled input for
something the user should not know is there. Absence also collapses the two questions a client
would otherwise have to distinguish — "forbidden" versus "unset" — into the only one it can act on.

**Why masking is server-side.** Sending the true value and letting the client render dots is a
hint, not a control. The value is still in the response body, the HTTP cache, the client's local
SQLite database, and any log that captures payloads.

**Why masks are for numbers, not text.** "The last four" is an idiom with exactly one purpose:
confirming the account you are about to pay into, or that a number on file matches the one in your
hand. Applied to free text it just hands over the end of the word. The first implementation masked
by length, and its own test caught `Colombo` becoming `••••ombo` — a redaction that redacts nothing
on every short address line in the product. The test is now what the value *is*: a numeric sequence
of at least eight digits, or nothing survives.

**Why roles combine to the most permissive grant.** Additive, consistent with every other
permission in the system. A role that *removes* access would force an administrator to reason about
the interaction of every role a user holds in order to predict what they can see — and role
assignment is done by people who are not systems thinkers. The safety property comes from the
sensitive-field default, not from letting one role veto another.

**Why record visibility is a separate question from field visibility.** `EmployeeService`
answers "may this caller open this record at all?" before the projection answers "which fields?".
Collapsing them gives one of two wrong systems: a manager who sees every field of their team's
records, or an HR administrator whose field grants let them enumerate the entire company. Only the
second is obvious, which is why they are kept apart structurally rather than by convention.

**Why a caller who may not see a record gets 404, not 403.** A 403 confirms the record exists.
Walk the id space, and the ids answering 403 are real employees — an enumeration oracle. The
distinction costs nothing, since the caller cannot act on the record either way.

## Consequences

**Accepted costs:**

- `EmployeeProjection.FIELDS` and `EmployeeWriter.SETTERS` must be extended when a column is added.
  A test asserts every settable field is also projectable, so the two cannot drift into separate
  key spaces — which would mean granting `WRITE` on `dateOfBirth` for reads and `date_of_birth` for
  writes, and one of them being forgotten.
- Employee responses are `Map<String, Any?>`, not a fixed DTO. A DTO would have to declare every
  field optional anyway, and would lose the "absent versus null" distinction the whole design turns
  on. Generated clients therefore see almost everything as optional; that is accurate, not a defect.
- A 60-second cache TTL on explicit grants bounds how long a *withdrawn* grant can linger when
  explicit eviction is missed. Same reasoning as `PermissionResolver`.
- `field_permission` is read via a hand-written join rather than JPA, because it is consulted per
  field per request and must not become an N+1.

**Enforcement:**

- `FieldPermissionResolverTest` — 20 tests covering the full default matrix. The cases that matter
  most are the ones asserting something *stays* hidden: sensitive fields under `canManage`, and
  ordinary fields not becoming writable just because they are readable.
- `FieldMaskerTest` — 13 tests asserting on what is **absent** from a masked value, not on what is
  present. A mask that leaks looks correct in a screenshot either way.
- `EmployeeProjectionTest` — hidden fields absent rather than null, true values absent from the
  whole payload, `id`/`version` surviving a policy that hides everything.
- `EmployeeWriterTest` — refusal rather than silent skip, nothing applied when any field is
  refused, leaver fields unreachable.

**Not yet verified:** the `field_permission` join itself has never executed — no PostgreSQL in the
build environment. The defaults around it are heavily tested with the query stubbed. See
`PHASE-1-STATUS.md`.

## Revisit when

- A customer needs field access to vary by *record* rather than by role — e.g. "the recruiter who
  hired them may see their offer letter". That is attribute-based and the current model cannot
  express it; `data_scope` is the intended home for it.
- Field-level access needs to be time-bounded (a payroll clerk who may see bank details only during
  a payroll run).
- The `ALWAYS_SENSITIVE` list grows past the point where a code change per column is reasonable —
  at which point the classification should move to a migration-declared column comment or a
  generated manifest, keeping it in version control while removing the Kotlin edit.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Deny everything by default | Unusable; predictably produces tenants that grant everything to everyone |
| Allow everything by default | Silent exposure of new sensitive columns, and self-writable `joinDate` |
| One uniform default for read and write | Reading a colleague's department is harmless; writing your own tenure is not |
| Jackson `@JsonFilter` | Binds to one serialisation path; a missing annotation looks like nothing in review |
| Reflective projection over entity fields | Exposes every newly added column by default |
| `ALWAYS_SENSITIVE` as tenant configuration | Lets a tenant expose their own employees' identity numbers by clearing a row |
| Null for hidden fields | Discloses that the field exists and is forbidden; invites disabled inputs for unknown data |
| Client-side masking | The true value is still in the payload, the cache, the local database and the logs |
| Most-restrictive role combination | Forces administrators to reason about role interaction to predict visibility |
| 403 for records the caller may not see | Confirms existence; turns the endpoint into an enumeration oracle |
