# The mobile home screen — design decision

**Status:** Decided, not built. Covers P1-BE-25, P1-BE-26, and the client tasks that depend on
them (P1-AND-07…10, P1-IOS-07…10).

**How this was produced.** Three designs were written independently from different angles —
offline-first, maximally server-driven, and minimal-and-fast — and each was judged by three
adversarial reviewers reading the actual repository. Every design scored 4–5 out of 10. That
unanimity is the most useful result: the reviewers were not splitting hairs between good options,
they were finding the *same rock* that all three had run onto.

This document records what the rock was, and what to build instead.

---

## 1. The finding that decides the design

**All three designs put the home screen's position state outside the tables it describes, and all
three produce a permanently blank home screen as a result.** Three reviewers found it by three
different routes.

The mechanism is identical each time:

1. Home data lands in local tables (`home_card`, `home_milestone`, …) via appliers registered in
   the shared `ChangeApplier` registry.
2. The freshness marker — an HTTP `ETag`, stored in a bespoke `home_meta` table — is deliberately
   *not* a `sync_cursor` row, on the grounds that home "is not a sync scope".
3. `SyncEngine.resetScope(scope)` iterates **every** applier in the registry and calls
   `clear(db, scope:)` on each, for any scope
   ([SyncEngine.kt:109-116](../ios/Sources/HRCore/Sync/SyncEngine.swift), same shape on Android).
4. So a `410 SYNC_CURSOR_EXPIRED` on an unrelated scope — a documented, expected event — wipes the
   home tables. The ETag, owned by nobody, survives.
5. The next refresh sends `If-None-Match`, receives `304`, and every design's rule is *"on 304 the
   client writes nothing at all."*

Home is now empty forever. It renders as the **designed empty state**, so it is indistinguishable
from a tenant that has configured no cards: no error, no retry, nothing in telemetry. Recovery is a
reinstall.

This is `docs/sync-protocol.md` §3.3's "cursor advanced, data not written" failure — the exact
scenario the protocol makes impossible on the delta path by persisting the cursor *inside the same
transaction as the data* — reintroduced by a design that thought it was avoiding cursors.

The justification for the bespoke table was also simply wrong. Every design claimed home could not
use `sync_cursor` because it has no cursor. It can: `cursor` is nullable on both platforms
(`SyncEntities.kt`, `AppDatabase.swift`) and there is a `lastSyncedAt` column. `scope='home',
cursor=NULL` is exactly the slot.

### The decision

**Home position state lives in `sync_cursor` under `scope='home'`, written in the same transaction
as the data it describes.** It is then swept by `resetScope` and `clearAll` for free, and the
failure above cannot occur.

More broadly: **home is a sync scope, not a special endpoint.** `GET /v1/mobile/home` exists as a
cold-start convenience that returns the same entities the `home` scope carries, in the same
envelope, applied by the same appliers — and it carries a cursor like everything else. It is not a
second write path with its own rules.

---

## 2. Do not use conditional requests here

Every design leaned on `ETag` + `304` as its main optimisation. Two independent problems, either
one disqualifying:

**Both generated clients treat 304 as an error.** The Swift client sets
`successfulStatusCodeRange = 200..<300` and *throws* `ErrorResponse.error(304, …)`. Retrofit
reports `isSuccessful == false` and a null body. So the response the design calls "the
overwhelmingly common case" is an error shape on both platforms — and the natural handling on each
(`response.body() ?: return` in Kotlin, `do/catch` → record a sync failure in Swift) produces
*different app behaviour from an identical server response*.

**A 304 costs the server as much as a 200.** The ETag is a content hash over the composed payload,
so it cannot be computed without composing the payload. Bandwidth is saved; database work is not.
The design that made this its headline optimisation never mentioned server cost.

**Decision:** no `If-None-Match` on this endpoint. Freshness comes from the sync cursor, which is
the mechanism the protocol already has and which the clients already handle.

---

## 3. Counts ship with their identities

Three of five reviewers independently called this out as worth taking regardless of which design
won, and they are right.

A card showing "12 pending on you" must not send `12`. It sends the total **and the identity set**
that composes it:

```json
{ "totalCount": 12, "countedEntities": [ {"entityType": "leaveApplication", "entityId": "…"}, … ] }
```

The client then displays `totalCount − |matching outbox entries in PENDING/IN_FLIGHT|`.

Without this, a user who approves three requests in airplane mode still reads "12", concludes
their taps did nothing, and taps again. The server cannot fix this from its side — it cannot see
the device's outbox — so the fix has to be a wire-format decision.

Above a cap the set is truncated and `countIsApproximate` is set, so the client knows to stop
subtracting rather than showing a confidently wrong smaller number.

---

## 4. No closed enums on the wire

`entityType` and card `type` are `type: string` with the known values listed **in the
description**, never an OpenAPI `enum`.

An `enum` generates a Kotlin `@Serializable enum class` and a Swift `enum: String, Codable`. Both
throw on an unknown value, and both abort decoding of the **entire response** — not the one field.
So the first tenant to enable a card type shipped after a user's last app update gets a blank home
screen, on every installed build, until they update.

This is the same reasoning as `FormField.Type` in the existing form schema, and the same trap: a
closed enum is the correct model of a closed set and the wrong model of a set the server may
extend.

---

## 5. Cards a caller may not see are absent — and that needs *both* gates

The most serious finding in the review, and it is about code that already exists.

A work-anniversary card seems safe: it shows a name and a date, and `joinDate` is not in
`FieldPermissionResolver.ALWAYS_SENSITIVE`, so the field check passes for every colleague. A
design that gates the card on `FieldPermissions.accessFor(…, "joinDate")` therefore emits
*"Nadeesha Perera — 5 years today"* for every active employee to every authenticated user.

But `V8__employee_access.sql` grants the `EMPLOYEE` role only `employee.directory` and
`org.reference.view` — neither `employee.view` nor `employee.view.all`. So those same users would
get a **404** from `GET /v1/employees/{id}` on the very records the card just listed. The card's own
`actionUri` proves the inconsistency: for the majority role, the primary action on every row is a
guaranteed 404.

The cause is that a per-field permission check is **not** equivalent to what `EmployeeService` does.
`assertVisible` answers "may this caller see this record *at all*?" — ownership, then
`employee.manage`/`employee.view.all`, then the reporting-line subtree — and its own doc comment
says keeping that separate from the field question *is the point*. Any assembler that checks fields
without also checking record visibility has silently dropped half the authorisation model.

**Decision:** every card that names an employee applies the record gate first and the field gate
second, through the same code path `EmployeeService` uses. If that makes the milestones card empty
for the default `EMPLOYEE` role, then the correct product answer is to grant `employee.directory`
holders an explicit, narrow directory-visibility rule — not to skip the check.

A related trap: enabling a birthday card by granting `dateOfBirth` READ is **role-scoped and
entity-wide**, not card-scoped. An admin turning on a month-and-day birthday card would necessarily
grant full date-of-birth READ, with year, everywhere that role can see a record. The month-day
truncation is a control on the card, not on the system it opens.

---

## 6. Cost model

The milestones query is a `(month, day)` predicate on `join_date` and `date_of_birth`. **Neither
column has an index**, and `V6` adds no expression index.

Apply the cadence every design proposed — foreground, push, pull-to-refresh, 15-minute periodic,
and after every outbox drain — to a 10,000-employee tenant at 09:00, and that is ~10,000 concurrent
per-user compositions each sequentially scanning the whole employee table. There is no rate limiter
in the backend, and the jitter in `Outbox.backoff` protects the write path, not this one.

**Decisions:**

- A migration adds expression indexes on `(tenant_id, month/day of join_date)` and the same for
  `date_of_birth`, filtered to `status NOT IN ('EXITED','PENDING_JOIN')`.
- Milestones are cached per tenant, not per user. Every field a milestone exposes
  (`displayName`, `designation`, `photoKey`) is default-READ for all colleagues, so a tenant-scoped
  cache leaks nothing. The designs banned all caching for a privacy reason that is correct for
  self-only cards and far too blunt for this one.
- The foreground trigger is jittered and single-flighted, for the same reason the outbox retry is.

---

## 7. What this endpoint is not

- **Not the render path.** Home paints from local tables in under 100 ms with the radio off; the
  network is a refresh. This was the one thing all three designs agreed on and got right.
- **Not a layout language.** The wire carries no expressions, no `showWhen`, no formulas, no nested
  containers, no string interpolation. The server evaluates every condition and every localisation;
  the wire carries results and a closed vocabulary of intents. The moment a card needs an `if`, the
  server does the `if`.
- **Not a second write path.** One resolver serves both this endpoint and the `home` sync scope. Two
  code paths producing "the same" payload diverge silently, and QA cannot falsify it because both
  outputs are individually correct.

---

## 8. Open questions

- **Row actions.** A closed intent vocabulary (`NAVIGATE`, `APPROVE`, `REJECT`, …) rather than a
  wire-carried `{method, path, body}` recipe — an HTTP recipe on the wire would make the outbox's
  per-aggregate ordering and idempotency guarantees unenforceable. The vocabulary itself is not yet
  enumerated.
- **Module gating.** `tenant_module` exists and is unread; `/v1/me` still returns a hardcoded
  `["identity"]`. A card belonging to a disabled module has no defined behaviour yet. Tracked
  separately.
- **What the default `EMPLOYEE` role should see**, given §5. This is a product decision, not an
  engineering one.

---

## 9. Things worth keeping from the losing designs

Recorded because they were verified against this repository rather than asserted:

- `SyncChange.payload` is already `String` on both platforms, and `ChangeApplier.upsert` already
  takes a `String`. Home data can flow through the existing applier registry with no new wire type.
- The ETag, if one is ever reintroduced, must be duplicated into the response body: the Swift
  client's convenience method returns `.execute().body` and discards the header dictionary
  entirely, so a header-only value would force iOS off the generated call path.
- Absolute dates on the wire (`expiryDate`, `onDate`), never server-computed countdowns. A
  countdown rots the moment the device goes offline; a date does not.
