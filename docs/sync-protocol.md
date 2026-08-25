# Mobile Sync Protocol

**Status:** v1 · Phase 0
**Applies to:** Android (`android/`), iOS (`ios/`), server (`com.hr.sync`)

This document is the contract between three independent implementations. It exists because the
sync engine is the largest single risk in Phase 0 (see
[phase-0-foundations.md](phases/phase-0-foundations.md)), and the way two mobile platforms diverge
is by each inferring the protocol from the server's behaviour rather than from a written
agreement.

**Read this before writing sync code on either platform.** Where an implementation must choose
between matching this document and matching the other platform, match this document and raise the
discrepancy.

Rationale for the overall design is in [ADR 0003](adr/0003-offline-first-mobile.md).

---

## 1. The core rule

> **The local database is the source of truth for the UI. The network is a background
> reconciliation process.**

No screen may await a network call for data it has previously displayed. A screen renders from
local state, and sync updates that state underneath it. This is the difference between "fast when
the connection is good" and "fast".

Concretely, on both platforms:

- View models observe the local database (Flow / AsyncSequence), never a network call.
- A user action writes locally and returns immediately; the network happens afterwards.
- A spinner is only ever acceptable on genuinely first-time-empty state.

---

## 2. Vocabulary

| Term | Meaning |
|---|---|
| **Scope** | A named slice of syncable data (`directory`, `leave`, `attendance`, …). Clients subscribe to scopes; the server decides which the user is entitled to. |
| **Cursor** | Opaque server-issued string marking a position in the change feed. Clients store it and send it back; they never parse it. |
| **Outbox** | Local queue of user mutations awaiting confirmation. |
| **Idempotency key** | Client-generated UUIDv7 identifying one logical mutation across any number of retries. |
| **Confirmed** | The server has durably applied a mutation and the client has recorded its authoritative result. |

---

## 3. Read path — delta sync

### 3.1 Request

```
GET /v1/sync?since=<cursor>&scopes=leave,attendance&limit=500
Authorization: Bearer <access token>
```

- `since` omitted → initial full sync of the subscribed scopes.
- `scopes` omitted → the server's default set for this user's roles.
- A scope the caller is not entitled to is **ignored, not rejected**. Clients must not need to
  know the permission model to construct a valid request.

### 3.2 Response

```json
{
  "changes": [
    { "entityType": "employee", "entityId": "0193...", "payload": { } }
  ],
  "deletes": [
    { "entityType": "leaveApplication", "entityId": "0193..." }
  ],
  "cursor": "opaque-string",
  "hasMore": true
}
```

### 3.3 Client algorithm

```
loop:
    response = GET /v1/sync?since=storedCursor
    in ONE local transaction:
        upsert every change
        delete every delete
        store response.cursor
    if not response.hasMore: break
```

**The cursor must be persisted in the same transaction as the data it accompanies.** If they are
written separately and the process dies between them, the client either loses changes (cursor
advanced, data not written) or reprocesses them (data written, cursor not advanced). The first is
silent data loss.

### 3.4 Ordering

The server orders by a **monotonic commit sequence**, not a timestamp.

This is the subtle part and the reason it is called out here rather than left to the server. With
`WHERE updated_at > :lastSync`, a row committed inside a long transaction can become visible
*after* a client has already synced past that timestamp — and the client then never sees it, with
no error and no way to detect the loss. Clock skew between server instances makes it worse. A
sequence assigned at commit has no such window.

**Clients must therefore treat the cursor as opaque.** Do not sort by it, compare it, or attempt
to derive a time from it.

### 3.5 Expired cursors

If the cursor is older than the retained change history (90 days), the server responds `410 Gone`
with code `SYNC_CURSOR_EXPIRED`. The client must discard its local store for the affected scopes
and perform a full initial sync. This is expected for a device that has been offline for months;
it is not an error state to surface to the user beyond a progress indicator.

### 3.6 Cadence

| Trigger | Behaviour |
|---|---|
| App foreground | Sync immediately |
| Push notification with `sync: true` | Sync immediately |
| Periodic background | Every 15 min (Android `WorkManager`, iOS `BGAppRefreshTask`) — both are best-effort and the OS may defer them |
| Manual pull-to-refresh | Sync immediately |
| After outbox drain | Sync immediately, to pick up server-side effects of our own writes |

Reference data (leave types, holidays, designations) syncs on a slower cadence with `ETag`.

---

## 4. Write path — the outbox

### 4.1 Sequence

1. User acts.
2. Write the change to the local database, marked `PENDING`. **The UI updates now.**
3. Append a row to the outbox: idempotency key, endpoint, payload, target entity.
4. A background worker drains the outbox.
5. On success: mark `CONFIRMED`, reconcile the local row with the server's authoritative version.
6. On business rejection: mark `REJECTED`, surface a non-blocking banner, **keep the user's input
   for editing**. Never silently discard what someone typed.

### 4.2 Ordering

Outbox entries are drained **in insertion order per aggregate**, and aggregates may drain
concurrently. "Apply for leave" then "cancel that leave" must not be reordered; one employee's
leave and another's attendance need not be serialised against each other.

The aggregate key is `entityType + entityId`.

### 4.3 Idempotency

Every outbox entry carries a client-generated UUIDv7 sent as `Idempotency-Key`.

```
POST /v1/leave/applications
Idempotency-Key: 0193f2a1-...
```

The server records the key and its outcome. A replay returns the original response rather than
applying the operation twice. **This is why an outbox may retry indefinitely without risking
duplicate leave applications or double attendance punches.**

Reusing a key with a *different* payload is a client bug and the server rejects it. Generate the
key once, when the row is created — never on retry.

### 4.4 Retry policy

| Outcome | Action |
|---|---|
| 2xx | Confirm. Remove from outbox. |
| 401 | Refresh the token, retry once. If refresh fails, pause the outbox and require re-authentication. |
| 409 `ALREADY_DECIDED` | Treat as success — someone else acted first. Refresh local state. |
| 4xx (other) | Terminal. Mark `REJECTED` with the error code. **Do not retry** — the request will never succeed. |
| 5xx / network | Retry with exponential backoff: 1s, 2s, 4s … capped at 5 min, with jitter. |
| Retries exceed 7 days | Mark `FAILED`, notify the user, keep the payload for manual retry. |

Jitter is not optional. Without it, every device in a company retries in lockstep after an outage
and the recovering server is immediately knocked over again.

### 4.5 Pausing

The outbox pauses entirely when authentication is invalid. It does **not** pause on network loss —
the workers simply do not run, which is what the OS schedulers already handle.

---

## 5. Conflict resolution

There is deliberately **no generic last-write-wins**. Each entity type declares a strategy,
because LWW is wrong for every entity type in this product.

| Entity | Strategy | Reasoning |
|---|---|---|
| Attendance punch | **Append-only** | A punch is a fact about the past. Two punches are two punches, never a conflict. |
| Leave application | **Server wins on state** | The balance may have changed underneath the client. If the server rejects, the client resubmits or shows the reason. |
| Profile edit | **Field-level merge** | Two people editing different fields should both succeed. Only genuinely conflicting fields are surfaced for the user to choose. |
| Approval decision | **First write wins** | The second approver gets `409 ALREADY_DECIDED` and a refresh. Silently overwriting someone's decision is unacceptable in an audit trail. |
| Reference data | **Server always wins** | Clients never author it. |

New entity types **must** declare a strategy before they are added to a sync scope.

---

## 6. Local state machine

Every syncable local row carries a sync state:

```
                    ┌──────────┐
      user action → │ PENDING  │
                    └────┬─────┘
                         │ drain succeeds
                    ┌────▼─────┐
                    │CONFIRMED │ ← server delta
                    └──────────┘
                         │
      drain 4xx     ┌────▼─────┐
      ───────────►  │ REJECTED │ → user edits → PENDING
                    └──────────┘
      7 days        ┌──────────┐
      ───────────►  │  FAILED  │ → manual retry → PENDING
                    └──────────┘
```

The UI must render `PENDING` and `REJECTED` distinctly — a queued item shows a subtle badge, a
rejected one shows why. A user must never be left believing something was submitted when it was
not.

---

## 7. What works offline

**Must work fully offline** (Phase 2 onward, as each feature lands):

Clock in/out · view leave balance and history · apply for leave · view already-downloaded
payslips · employee directory · org chart · own profile · holiday calendar · view pending
approvals and queue decisions · document vault · already-fetched announcements.

**Requires network, and must degrade explicitly** (a disabled control with a reason, never a
mysterious failure):

Payroll run monitoring · report generation · first-time document download · assistant queries ·
first login.

---

## 8. Security

- The local database is encrypted: SQLCipher on Android, file protection
  `.completeUnlessOpen` plus an encrypted GRDB store on iOS.
- Payslip PDFs are **never** cached to disk unencrypted.
- On sign-out or device revocation the local store is **destroyed**, not merely cleared — a
  revoked device must not retain a readable copy of the tenant's data.
- Outbox payloads may contain personal data and are encrypted at rest with the same key.

---

## 9. Testing requirements

Both platforms must pass equivalent tests. These are the scenarios from `P0-QA-05`:

| # | Scenario | Expectation |
|---|---|---|
| 1 | Kill the process mid-sync | Resume from the last persisted cursor; no loss, no duplication |
| 2 | Deliver the same mutation twice | Exactly one server-side effect |
| 3 | Device clock set 24h forward or back | No effect on ordering or dedup |
| 4 | Changes arrive out of order | Final state is correct |
| 5 | 7 days offline, 500 queued mutations | All drain in aggregate order |
| 6 | Cursor expires (410) | Full resync, no crash, no duplicate rows |
| 7 | Token expires mid-drain | Refresh and continue, no lost entries |
| 8 | Business rejection mid-drain | That entry is `REJECTED`, the rest continue |
| 9 | Airplane mode toggled repeatedly during drain | No duplicates, no lost entries |
| 10 | Sign out with a non-empty outbox | Store destroyed; queued mutations discarded with warning |

---

## 10. Open items for v2

Recorded so they are not rediscovered as bugs:

- **Payload resolution per entity type.** The server's `/v1/sync` returns `payload`, but the
  resolvers do not exist yet — which is why the endpoint is currently absent from the OpenAPI
  spec (P0-BE-33). Until it lands, clients have no delta source and must fetch directly.
- **Scope subscription negotiation.** Currently the server infers scopes from roles. A client
  that only shows the leave screen still syncs everything it is entitled to.
- **Partial-scope invalidation.** A permission change today requires a full resync of the
  affected scope.
- **Compression.** Delta responses are uncompressed JSON. Worth revisiting once real payload
  sizes are known.
