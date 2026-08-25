# iOS client

**Status: skeleton, unbuilt.** Written on Windows, where there is no Swift toolchain — see the
caveat at the bottom. Expect to fix compilation errors on first build.

---

## Layout

```
ios/
├── Package.swift              HRCore — the non-UI layers, built and tested with `swift test`
├── Sources/HRCore/
│   ├── Security/              Keychain + Secure Enclave token sealing
│   ├── Persistence/           GRDB database, migrations, file protection
│   └── Sync/                  Outbox, sync engine, protocol types
├── Tests/HRCoreTests/         Mirrors the Android unit tests by name
└── App/                       SwiftUI app target (Xcode project — see below)
```

The data layers live in a Swift package rather than inside the app target so they are testable
without launching an app and buildable in CI with `swift test` alone.

---

## Getting started

```bash
cd ios && swift build
```

```bash
cd ios && swift test
```

### Creating the Xcode app target

Not committed, because a `.pbxproj` is unreadable in review and merges badly. Create it once:

1. **File → New → Project → iOS → App.** Name `HR`, interface SwiftUI, language Swift, save into
   `ios/App/`.
2. Set the deployment target to **iOS 16.0** to match `Package.swift`.
3. **File → Add Package Dependencies → Add Local** and select `ios/` (the `HRCore` package).
4. Add `HRCore` to the app target's *Frameworks, Libraries and Embedded Content*.
5. In *Signing & Capabilities* add **Background Modes** → *Background fetch* and *Background
   processing*, which the sync and outbox schedulers need.
6. Add to `Info.plist`:
   - `NSFaceIDUsageDescription` — *"Unlock HR with Face ID so you don't have to type your
     password."*
   - `NSLocationWhenInUseUsageDescription` — *"Used only to confirm your location when you clock
     in, and only if your employer requires it."*
   - `BGTaskSchedulerPermittedIdentifiers` — `io.hrapp.sync`, `io.hrapp.outbox`

**On the location string:** it is worded to say *"only if your employer requires it"* because that
is true — location capture is a server-issued per-tenant policy, and when a tenant sets it to
`off` the app never requests the permission at all. This is the direct fix for the most-complained-
about behaviour in the product we are replacing.

---

## Architecture

Everything here implements [docs/sync-protocol.md](../docs/sync-protocol.md), which is the written
contract shared with Android. **Read it before changing anything in `Sync/`.** Where an
implementation must choose between matching that document and matching Android's behaviour, match
the document and raise the discrepancy.

| Component | Android counterpart | Notes |
|---|---|---|
| `SecureTokenStore` | `SecureTokenStore` | Keychain `.biometryCurrentSet` vs Keystore `setUserAuthenticationRequired` |
| `Outbox` | `Outbox` | Identical states, identical backoff formula |
| `SyncEngine` | `SyncEngine` | Identical cursor-in-transaction semantics |
| `AppDatabase` | `HrDatabase` + `DatabaseKeyProvider` | File protection vs SQLCipher — see below |

### Why no KMP

[ADR 0004](../docs/adr/0004-no-kotlin-multiplatform.md). Briefly: the API models are already
shared via generation from the OpenAPI spec, the protocol is shared via the document, and the
genuinely hard parts of sync — `BGTaskScheduler` versus `WorkManager`, Secure Enclave versus
Keystore — are platform-specific anyway and could not be shared regardless.

### Why file protection rather than SQLCipher

Android uses SQLCipher because the platform offers no equivalent guarantee. iOS does:
`.completeUnlessOpen` encrypts the database with a key derived from the device passcode whenever
the device is locked. Layering SQLCipher on top would double the cryptography without meaningfully
raising the bar against the actual threat, which is offline extraction from a stolen device.

---

## Not yet built

- The SwiftUI app target and every screen (Phase 1)
- `URLSession` networking layer and the token-refresh interceptor
- `BGTaskScheduler` registration for sync and outbox
- `OutboxSender` implementation — the protocol is defined, the HTTP client is not
- `SyncAPI` implementation — blocked on the server endpoint (P0-BE-33)

---

## Caveat

Every file in this directory was written on Windows. There is no Swift toolchain here, so **none
of it has been compiled and none of the tests have been run.** The design mirrors the Android
implementation, which is verified — but treat first-build errors as expected rather than
surprising, particularly around GRDB's API surface and actor isolation.
