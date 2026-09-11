# HR Admin Console

React 19 + TypeScript + Vite. The desk-bound half of the platform: the configuration and
administration screens that don't belong on a phone.

**Status: skeleton.** Auth, routing, layout, design tokens and the generated-client wiring all
work. The three admin screens are placeholders — see below.

---

## Running it

```bash
npm install
```

```bash
npm run dev
```

Opens on `http://localhost:5173`. The dev server proxies `/v1` to `http://localhost:8080`, so the
backend must be running (`docker compose up -d` then `cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'`).

Sign in with the local demo account: organisation `demo`, username `admin`, password
`DemoPassw0rd!`.

```bash
npm run typecheck
```

```bash
npm run build
```

---

## Demo mode — running the whole console with no backend

```bash
npm run demo
```

Opens on `http://localhost:5173` with **no server required**: the Vite dev server answers every
`/v1` request itself, from in-memory fixtures in [`demo/`](demo/). The React application is not
modified or aware of this in any way — it makes real `fetch` calls through the generated client,
refreshes real tokens and handles real error envelopes. Only the thing on the other end of the
socket is different.

Equivalent, if you would rather set a flag than a mode: `VITE_DEMO=1 npx vite`. The npm script uses
`--mode demo` because `VITE_DEMO=1 vite` is bash syntax and does not work in PowerShell or
`cmd.exe`.

### Signing in

The same organisation and accounts as the backend's `LocalDemoSeeder`, so a demo run and a local
backend run look like the same company. Organisation code **`demo`**.

| Username | What it is there to show |
|---|---|
| `admin` | Every permission; linked to the CEO's record. Holds explicit grants over date of birth and personal email, so it can edit them. |
| `hr` | HR_ADMIN. May edit anybody's record and still may **not** see their date of birth — the separation the field-permission design exists for. |
| `manager` | `employee.view` without `employee.view.all`. Sees its own reporting subtree; anyone else is a 404, not a 403. |
| `employee` | The directory only. Its own record is authorised by ownership, not by a grant; a colleague's profile is a 404. |
| `locked` | Always refused with `403 ACCOUNT_LOCKED`, so the sign-in error path can be seen. |

**Any non-empty password works.** There are no password hashes in the fixtures to check one
against, and a demo that can lock you out of itself over a typo is a demo nobody runs twice.
`DemoPassw0rd!` is what the backend seeder uses and what the startup banner quotes.

The same details are printed to the terminal every time the dev server starts, so nobody has to
find this file first.

### What is stateful

Everything is in memory and resets when the dev server restarts.

- Signing in issues a session. Refresh tokens rotate and are single-use; presenting a spent one
  answers `TOKEN_REUSE_DETECTED` and revokes the family, exactly as the real server does.
- `PATCH`ing a profile changes what the next `GET` returns, and bumps `version` — so opening the
  same profile in two tabs and saving both gives you a real `409 STALE_VERSION`.
- Revoking a device removes it from the list. Revoking **this browser's** device ends the session
  at the next reload rather than mid-click, because the contract says an already-issued access
  token stays valid for its full fifteen minutes.
- Enrolling in two-factor flips the status and returns recovery codes **once**. Any six digits are
  accepted; `000000` is always rejected, so `MFA_INVALID_CODE` is reachable.
- Saving notification settings persists for the session, stored sparsely — only what differs from
  the defaults, as the server stores it.

There are 32 employees over four reporting levels, one of whom has left (and is therefore excluded
from the directory), so the directory paginates at the console's page size of 25 and
`direct reports` returns something.

### Why it is a dev-server plugin and not a flag in the app

Because a flag in the app puts the fixtures in `src/`, which puts them in the production bundle,
where the only thing standing between a demo company and a customer is an environment variable
nobody set on purpose. Three independent things stop that here:

1. Nothing under `src/` imports anything under `demo/`. The only importer is `vite.config.ts`,
   which Rollup never sees — there is no module graph from the application entry point to these
   fixtures.
2. The plugin declares `apply: 'serve'`, so Vite will not run it during `vite build`.
3. Its only hook is `configureServer`, which exists solely on the dev server. It has no
   `transform`, no `resolveId` and no `generateBundle` — nothing that could emit a byte.

`npm run build` and `VITE_DEMO=1 npx vite build --mode demo` produce byte-identical bundles.

---

## Why a proxy rather than pointing at localhost:8080

The browser then sees same-origin requests, so no CORS configuration is needed in development and
the dev setup matches production, where the console is served behind the same host as the API.
Configuring CORS for a development convenience is how permissive CORS ends up in production.

---

## Token storage — a known weakness

Read [`src/lib/tokens.ts`](src/lib/tokens.ts) before changing anything about auth.

The short version: the access token is held **in memory only**, and the refresh token in
**`sessionStorage`** (not `localStorage` — a shared workstation is exactly where an HR console
runs). This is still XSS-exposed: any script running in this origin can read the refresh token.

The proper fix is server-side — issue the refresh token to browser clients as an
`HttpOnly; Secure; SameSite=Strict` cookie so script cannot read it at all. That needs a
web-specific variant of the token endpoints on the backend and is recorded as a known gap in
[PHASE-0-STATUS.md](../PHASE-0-STATUS.md). It should be closed before the console handles real
payroll data.

---

## Concurrent refresh

`src/lib/api.ts` funnels all token refreshes through a single in-flight promise. This is not an
optimisation — it is required for correctness. Refresh tokens are single-use and rotate, so six
queries firing on mount and each triggering its own refresh would present an already-spent token
five times. The server treats that as theft and revokes the entire family, signing the user out.

---

## Structure

```
src/
├── lib/
│   ├── tokens.ts      Token storage and the trade-off behind it
│   ├── api.ts         Generated-client config, refresh interceptor, error mapping
│   └── auth.tsx       Auth context, session resumption, permission checks
├── components/
│   ├── ui.tsx         Design system primitives
│   └── ui.css
├── routes/
│   ├── SignIn.tsx
│   ├── AppLayout.tsx  Shell with permission-filtered navigation
│   ├── Overview.tsx
│   └── Placeholder.tsx
├── styles.css         Design tokens — mirrors the Android theme
└── shell.css
```

The API client is generated from `spec/openapi.yaml` and imported as `@hr/client`. Never edit it;
run `cd backend && ./gradlew generateAllClients`. See [clients/README.md](../clients/README.md).

---

## Not built yet

| Screen | Task |
|---|---|
| Organisations — create, configure, module toggles | P0-WEB-05 |
| Users — create, assign roles, reset password, revoke devices | P0-WEB-06 |
| Roles — compose from the permission catalogue | P0-WEB-07 |

These render a `Placeholder` that states what is missing and which task delivers it, rather than a
convincing table of invented rows. A placeholder that looks like a working screen gets demoed as
one, and the gap surfaces later as a surprise.

They do still enforce their permission check, so the routing and authorisation wiring is exercised
now rather than arriving with the real screen.

---

## Accessibility

Not a later pass. Already in place: visible focus rings on everything, labelled inputs with
`aria-describedby` wiring for hints and errors, `role="alert"` on error messages, `aria-busy` on
loading buttons, table captions for screen readers, and `prefers-reduced-motion` honoured.

Status is never conveyed by colour alone — every badge carries a label.
