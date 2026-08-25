# Overlays

Each overlay needs a `generated/` directory before `kustomize build` will succeed. It is
gitignored and produced from Terraform outputs:

```bash
node infra/scripts/render-overlay.mjs --environment staging --terraform-output /tmp/tf.json --image <repo>@sha256:<digest>
```

## Why generated rather than committed

The alternative — committing these files and having CI check they are fresh, as the API clients
do — was rejected because they carry AWS account ids and internal hostnames. Not secret, but
reconnaissance value, and it would be inconsistent with gitignoring `*.auto.tfvars` for exactly
the same reason.

The cost is that a fresh checkout cannot `kustomize build` until the render script has run. That
is deliberate: a missing file fails loudly, whereas the placeholders this replaced (`000000000000`
role ARNs) failed at pod-start with an IAM error nobody would connect to a copy-paste from three
weeks earlier.

## Why the image must be a digest

`render-overlay.mjs` rejects a tag reference. A tag can move, which makes a rollback ambiguous and
a rollout non-reproducible — if `:staging` pointed at a different image yesterday, "roll back to
yesterday's build" has no well-defined meaning.
