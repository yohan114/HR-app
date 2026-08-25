# Generated API clients

**Do not edit anything in `kotlin/`, `swift/` or `typescript/` by hand.** Every file there is
generated from [`spec/openapi.yaml`](../spec/openapi.yaml) and will be overwritten.

```bash
cd backend && ./gradlew generateAllClients
```

| Directory | Consumer | Stack |
|---|---|---|
| `kotlin/` | Android app | Retrofit 2 + kotlinx.serialization + coroutines |
| `swift/` | iOS app | Swift Package, async/await over URLSession |
| `typescript/` | Web admin console | Fetch API, strict-mode TypeScript |
| `verify/` | CI only | Compilation check for the Kotlin client — not shipped |

---

## Why the output is committed

The iOS and web projects do not run Gradle. If generation were build-time-only, those teams would
have to adopt a JVM toolchain just to obtain a client. Committing the output also makes contract
changes reviewable: a pull request that edits the spec shows exactly what it does to all three
clients, which is the cheapest possible way to catch an accidental breaking change.

CI regenerates on every PR and fails if the committed output is stale, so the two cannot drift.

---

## How each client is consumed

**Android** — the app module adds `clients/kotlin/src/main/kotlin` as an extra source directory
and supplies the dependencies itself (Retrofit, OkHttp, kotlinx.serialization). There is
deliberately no nested Gradle build here; a wrapper inside a wrapper is confusing and buys
nothing.

**iOS** — `clients/swift` is a Swift package. Add it as a local package dependency in Xcode. The
manifest targets iOS 16 and pulls in `AnyCodable`, which the generated models need for the
free-form `details` field on `ApiError` (Swift has no native arbitrary-JSON type).

**Web** — import from `clients/typescript` directly. It type-checks under `--strict` and has no
runtime dependencies beyond `fetch`.

---

## Regenerating individually

```bash
cd backend && ./gradlew generateKotlinClient
```

```bash
cd backend && ./gradlew generateSwiftClient
```

```bash
cd backend && ./gradlew generateTypeScriptClient
```

---

## Notes on the generated output

**Free-form JSON.** The `details` bag on `ApiError` is genuinely untyped — it carries whatever
structured context an error needs (`{"available": 2.5, "requested": 5}`). Each language handles
that differently:

| Language | Type | Why |
|---|---|---|
| Kotlin | `Map<String, JsonElement>` | `Map<String, Any>` is unserialisable by kotlinx.serialization — the compiler plugin crashes rather than reporting it |
| Swift | `[String: AnyCodable]` | Swift has no native arbitrary-JSON type |
| TypeScript | `{ [key: string]: any }` | Native |

If you add a new free-form object to the spec, expect to revisit the Kotlin `typeMappings` in
`backend/build.gradle.kts`.

**Scaffolding is stripped.** The generators emit build files, wrappers, READMEs and packaging
manifests on the assumption that their output is a standalone library. Ours is not — it is
consumed as source. The Gradle tasks delete that scaffolding, and write `Package.swift`
themselves so the Swift package targets iOS 16 rather than the generator's iOS 11 default.

**The server side is enforced separately.** These clients guarantee that consumers match the spec.
That the *server* matches it is asserted by `ApiContractTest` in the backend, which fails the
build if any documented operation is unimplemented, any implemented endpoint is undocumented, or
path parameter names disagree.
