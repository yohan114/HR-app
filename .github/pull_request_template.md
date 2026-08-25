## What and why

<!-- What changes, and what problem it solves. Link the task ID (e.g. P1-BE-07). -->

## Checklist

Delete any line that genuinely does not apply — do not tick it because it "probably" holds.

- [ ] Tests cover the change, including the failure paths
- [ ] Multi-tenant isolation holds: new tenant-scoped tables call `apply_tenant_rls()` and
      `tenant_id` leads every index on them
- [ ] Permission checks applied (RBAC, data scope, field level) where the change touches data
- [ ] Audit entries written for anything that mutates
- [ ] `spec/openapi.yaml` updated and clients regenerated if the API surface changed
- [ ] Both mobile platforms are at parity, or a follow-up issue is linked
- [ ] All six screen states handled: loading, loaded, empty, error, offline, no-permission
- [ ] Accessibility: screen reader labels, dynamic type, contrast, focus order
- [ ] Strings externalised; RTL verified if layout changed
- [ ] Performance budgets still met
- [ ] No secrets, tokens or credentials added to the repository

## Risk

<!-- What breaks if this is wrong, and how would we notice? "Nothing" is rarely true. -->

## Rollback

<!-- How to undo this. If it includes a migration, say whether it is reversible. -->
