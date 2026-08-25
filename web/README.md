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
