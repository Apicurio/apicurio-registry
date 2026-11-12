# Storage variants

The same Apicurio Registry image on every storage variant, with the same checks on each, and what
makes each one different:

- **SQL** on PostgreSQL (two replicas, one database), MySQL and SQL Server.
- **KafkaSQL**: two replicas sharing one Kafka journal topic.
- **GitOps**: a read-only registry loading a git repository that `demo.sh` creates and commits to,
  including a broken commit that is reported and not applied.
- **KubernetesOps**: a read-only registry loading ConfigMaps from a k3s server.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq` and `git`. KubernetesOps runs k3s
privileged; SQL Server's image is amd64 only and is skipped elsewhere.

```bash
./demo.sh                          # every variant, one after another
VARIANTS="gitops" KEEP=1 ./demo.sh # one variant, left running; the console is on :8888 for gitops
```

`demo.sh` starts each variant with `docker compose --profile <variant> up`, checks it, removes it,
and ends with `All checks passed.`.

## Services

Each Compose profile is one variant; the registry is on http://localhost:8081.

| Profile | Services |
|---|---|
| `postgresql` | `postgres`, `registry-pg-a` (8081), `registry-pg-b` (8082) |
| `mysql` | `mysql`, `registry-mysql` |
| `mssql` | `mssql`, `registry-mssql` |
| `kafkasql` | `kafka`, `registry-kafkasql-a` (8081), `registry-kafkasql-b` (8082) |
| `gitops` | `registry-gitops`, `registry-ui` (8888) |
| `kubernetesops` | `k3s`, `k8s-setup`, `registry-kubernetesops` |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough, how to choose a variant, presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | Every variant, one profile each; the storage settings are in each registry's `environment` |
| [`gitops-seed/`](gitops-seed) | The registry files and schemas: the GitOps repository's first commit, and the versions the demo adds |
| [`configmaps/`](configmaps) | The same data as ConfigMaps, before and after an update |
| [`k8s/setup.sh`](k8s/setup.sh) | Namespace, ConfigMaps, the registry's service account and kubeconfig in k3s |
| [`demo.sh`](demo.sh) | Runs and checks each variant |
| [`images/`](images) | The guide's screenshot; [`capture-screenshots.cjs`](capture-screenshots.cjs) refreshes it |
| [`ci.env`](ci.env) | CI settings: one job per variant |

## Notes

- **Known issues:** KafkaSQL replicas starting together can fail to create the topics
  ([#11079](https://github.com/Apicurio/apicurio-registry/issues/11079)); KubernetesOps applies
  changes only after the debounce maximum ([#11080](https://github.com/Apicurio/apicurio-registry/issues/11080)).
  The example works around both.
- **GitOps sync:** the registry never fetches. For a sidecar that pulls a remote repository or
  accepts `git push`, see [`distro/gitops/compose`](../../distro/gitops/compose/).
- **Local only:** default passwords, no TLS, short polling periods, k3s privileged.

## Learn more

- *Installing Registry storage*, *Configuring GitOps storage*, *Configuring KubernetesOps storage* in
  the Registry documentation ([source](../../docs/modules/ROOT/pages/getting-started/)).
- [ADR-0010](../../adr/0010-storage-architecture.md): how storage works.
- The storage support matrix in [DEVELOPING.md](../../DEVELOPING.md).
