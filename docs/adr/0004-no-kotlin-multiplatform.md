# ADR 0004 — Two native data layers, not Kotlin Multiplatform

- **Status:** Accepted
- **Date:** 2026-08-22
- **Phase:** 0 (gate decision, due end of week 4)
- **Supersedes:** the "decide at the Phase-1 gate" placeholder in [03-architecture.md](../03-architecture.md)

## Context

[03-architecture.md](../03-architecture.md) left one decision open: whether to share the mobile
**data layer** — sync engine, outbox, models, validation — between Android and iOS via Kotlin
Multiplatform, with UI remaining fully native on both sides.

The argument for KMP is real. The sync engine is the most intricate and highest-risk code in the
mobile clients, and writing it twice means two chances to get conflict resolution, cursor
persistence and outbox ordering subtly wrong in different ways.

The decision was scheduled for the end of Phase 0 week 4 so it would be made with the server
contract settled rather than speculatively.

## Decision

**No Kotlin Multiplatform. Two hand-written data layers**, one per platform, both implementing
[docs/sync-protocol.md](../sync-protocol.md) and both consuming generated API clients.

## Rationale

**The duplication KMP would remove is smaller than it looks.** Three of the four things we would
share are already shared by other means:

| Concern | Already shared by |
|---|---|
| API models and endpoints | Generated from `spec/openapi.yaml` — Kotlin and Swift clients are produced by the same tool from the same source |
| Protocol semantics | `docs/sync-protocol.md`, written before either implementation |
| Test scenarios | The ten cases in §9 of the protocol document, run identically on both platforms |
| Sync engine implementation | **Genuinely duplicated.** This is the real cost. |

So KMP buys us one component, not a layer.

**The platform-specific surface is exactly where the risk is.** The sync engine's hard parts are
its edges: `WorkManager` versus `BGTaskScheduler` scheduling semantics, Android Keystore versus
Secure Enclave key invalidation, SQLCipher versus GRDB transaction behaviour, doze mode versus
iOS background budgets. None of that is shareable. A KMP core would still need two platform
implementations of the parts most likely to be wrong, plus the `expect`/`actual` machinery
connecting them — which is net *more* moving parts, not fewer.

**Build complexity lands on the critical path.** KMP means the iOS build depends on a Kotlin
toolchain producing an XCFramework. That inserts a JVM build step into the iOS team's edit-run
loop, complicates their CI, and makes Xcode debugging of shared code awkward. Phase 0 has already
demonstrated how much time environment friction costs; adding a cross-compilation step before
either app renders a screen is the wrong sequencing.

**Team shape.** One Android engineer and one iOS engineer. KMP pays off when a shared-code team
serves several client teams. With one engineer per platform, the shared module has no owner — and
in practice becomes owned by whoever is less able to refuse it.

**Reversibility is asymmetric, and favours native.** Extracting a well-factored Kotlin data layer
into a KMP module later is mechanical. Unwinding a KMP module that has become friction is not:
by then the iOS build, CI and debugging workflow all depend on it. Starting native keeps the
cheaper option open.

## Consequences

**Accepted costs:**

- The sync engine is implemented twice, so a bug fixed on one platform can persist on the other.
  Mitigated by the shared protocol document, the shared test scenario list, and a parity check in
  the definition of done.
- Model validation logic is duplicated. Kept deliberately thin — the server validates
  authoritatively and returns machine-readable codes, so clients validate only for immediate
  feedback.
- Two conflict-resolution implementations. Mitigated by the strategy table in §5 of the protocol
  document being normative rather than descriptive.

**Retained:**

- Native build, debug and profiling tooling on both platforms, unmodified.
- Full control of app size — relevant against a 25 MB Android budget.
- The option to adopt KMP later for a specific component that proves genuinely duplicative.

**Enforcement:**

- Every sync change references a section of `docs/sync-protocol.md`. A behavioural change that is
  not in that document is not merged.
- The ten scenarios in §9 exist as tests on both platforms with matching names.

## Revisit when

- A third client platform appears (desktop, or a second mobile app), making one shared core serve
  three consumers instead of two.
- The mobile team grows past roughly six engineers, enough to own a shared module properly.
- The two sync implementations demonstrably diverge in behaviour despite the protocol document —
  that would be evidence the document is insufficient and code-level sharing is required.

## Alternatives considered

| Alternative | Why not |
|---|---|
| KMP for the data layer | Shares one component while adding a cross-compilation step to the iOS critical path; the riskiest code is platform-specific anyway |
| KMP for everything including UI (Compose Multiplatform) | Contradicts the native decision the user already made, and forfeits platform-idiomatic UX — the whole basis of our differentiation |
| Flutter / React Native | Already rejected; the incumbent's 92 MB / 276 MB binaries and "very slow" reviews are the visible cost |
| Shared C++/Rust core | Maximum portability, worst ergonomics; nothing here is compute-bound enough to justify it |
