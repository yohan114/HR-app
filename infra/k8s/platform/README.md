# Platform components

Cluster-wide components the application depends on but does not own. Installed once per cluster,
before the first application deploy.

| Component | Purpose |
|---|---|
| `external-secrets` | Materialises Kubernetes Secrets from AWS Secrets Manager |
| `ingress-nginx` | Internet-facing load balancer and TLS termination |
| `cert-manager` | Issues and renews Let's Encrypt certificates |
| `external-dns` | Keeps Route 53 records in step with Ingress resources |

---

## Why Helm here and Kustomize for the application

These are third-party components with large, frequently-changing manifests and their own upgrade
semantics. Vendoring them into Kustomize would mean re-vendoring on every upgrade and hand-merging
whatever the maintainers changed.

The application is ours, changes constantly, and needs per-environment patching — which is what
Kustomize is good at and Helm is awkward at. Using both is not indecision; they are solving
different problems.

---

## Installation order

The order matters. Each step depends on the previous one:

```bash
kubectl apply -f namespaces.yaml
```

```bash
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets --values external-secrets-values.yaml
```

**Then apply the generated service account patch**, which carries the IRSA role annotation from
Terraform:

```bash
kubectl apply -f infra/k8s/overlays/staging/generated/external-secrets-serviceaccount-patch.yaml
```

```bash
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --values cert-manager-values.yaml --set crds.enabled=true
```

```bash
kubectl apply -f cluster-issuer.yaml
```

```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --values ingress-nginx-values.yaml
```

```bash
helm upgrade --install external-dns external-dns/external-dns \
  --namespace external-dns --values external-dns-values.yaml
```

**cert-manager before the ClusterIssuer**: the issuer is a CRD that does not exist until
cert-manager has installed it. Applying in the wrong order gives a "no matches for kind" error
that reads like a typo.

---

## Not automated yet

These are applied by hand. That is acceptable for components installed once per cluster and
upgraded deliberately, but it means a rebuilt cluster needs someone to remember this list.

The proper answer is a GitOps controller (Argo CD or Flux) reconciling both these components and
the application from the repository — worth doing when there are more than two clusters, or when
the first "why is staging different from production?" investigation costs an afternoon.

---

## Values files contain no secrets

Route 53 access and Secrets Manager access both come from IRSA roles created by Terraform, so no
AWS credential appears in any of these files. The only value needing care is the ACME registration
email in `cluster-issuer.yaml`, which is not a secret but should be a team alias rather than an
individual.
