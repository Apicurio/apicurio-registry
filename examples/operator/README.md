# Operator

The Apicurio Registry operator on a local kind cluster: one `ApicurioRegistry3` custom resource per
storage, and what the operator does with each.

- **In memory:** the smallest CR; the deployments, services and network policies the operator
  creates and owns; configuration through `spec.app.env`; deleting the CR.
- **PostgreSQL:** the storage settings the operator sets, the password from a Secret, data that
  survives a pod.
- **KafkaSQL:** two replicas sharing a journal topic, and the PodDisruptionBudget that comes with them.
- **An invalid CR:** reported in the CR's status.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires [kind](https://kind.sigs.k8s.io/), `kubectl`, `curl`, `jq`, Docker (or Podman with
`KIND_EXPERIMENTAL_PROVIDER=podman`), and, for the first run, JDK 21 and `envsubst`.

```bash
./demo.sh          # build the operator, create the cluster, check, delete the cluster
KEEP=1 ./demo.sh   # leave the cluster running (kind-apicurio-operator-example)
```

It ends with `All checks passed.`. `build.sh` builds the operator image and `target/install.yaml`
from this checkout; `REGISTRY_IMAGE` and `REGISTRY_UI_IMAGE` select the registry images it deploys
(default `latest-snapshot`).

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`k8s/registry-in-memory.yaml`](k8s/registry-in-memory.yaml) | The smallest `ApicurioRegistry3` |
| [`k8s/registry-postgresql.yaml`](k8s/registry-postgresql.yaml), [`k8s/postgresql.yaml`](k8s/postgresql.yaml) | PostgreSQL storage, and a demo database |
| [`k8s/registry-kafkasql.yaml`](k8s/registry-kafkasql.yaml), [`k8s/kafka.yaml`](k8s/kafka.yaml) | KafkaSQL storage with two replicas, and a demo Kafka |
| [`build.sh`](build.sh) | Builds the operator image and its install file |
| [`demo.sh`](demo.sh) | Creates the cluster, installs the operator, checks each CR |
| [`ci.env`](ci.env) | CI settings: no Compose file, the demo brings its own cluster |

## Notes

- To install a released operator instead, use `operator/install/install.yaml` (or OperatorHub) and
  skip `build.sh`; the CRs are the same.
- **Local only:** demo databases without volumes, plain HTTP, no authentication.

## Learn more

- The operator documentation ([source](../../operator/docs/modules/ROOT/pages/)): installation,
  configuration reference, storage.
- [ADR-0019](../../adr/0019-operator.md): how the operator is built.
- [`storage-variants`](../storage-variants/): the same storage choices with Docker Compose.
