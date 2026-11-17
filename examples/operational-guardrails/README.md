# Operational guardrails

The switches that bound what clients can do with a registry, checked on two replicas sharing one
database:

- **Limits** (`apicurio.limits.config.*`): names, descriptions, schema size, labels, versions per
  artifact, artifacts; refused with 409 and a message naming the limit.
- **Disabled APIs** (`apicurio.disable.apis`): whole API paths answer 404.
- **Deletion switches** (`apicurio.rest.deletion.*`): off by default, switched on at runtime.
- **Read-only mode** (`apicurio.storage.read-only.enabled`): every write refused, switched at
  runtime, followed by every replica.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
./demo.sh          # start from an empty database, check, remove everything
KEEP=1 ./demo.sh   # leave the stack running
```

It ends with `All checks passed.`.

## Services

| Service | Port | Role |
|---|---|---|
| `postgres` | | The database both replicas share |
| `registry-a` | 8081 | A replica, with the limits and disabled APIs in its `environment` |
| `registry-b` | 8082 | A second replica, the same configuration |

Override the ports with `REGISTRY_PORT` and `REGISTRY_B_PORT`.

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | The stack and every setting |
| [`demo.sh`](demo.sh) | Starts the stack and checks each guardrail |
| [`ci.env`](ci.env) | CI settings |

## Notes

- **Known issues**, shown or described:
  - [#11218](https://github.com/Apicurio/apicurio-registry/issues/11218): the artifact and version
    count limits check counters that can be stale, so a cluster can hold more than the limit.
  - [#11219](https://github.com/Apicurio/apicurio-registry/issues/11219):
    `max-requests-per-second` is accepted but not enforced; rate-limit in your gateway.
  - [#11222](https://github.com/Apicurio/apicurio-registry/issues/11222): the deletion switches are
    not checked by the Confluent-compatible API.
- **Local only:** no authentication, default passwords.

## Learn more

- *Operational guardrails* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc)).
- [ADR-0017](../../adr/0017-operational-guardrails.md): the design.
- [Storage variants](../storage-variants/): read-only mode on every variant, and the read-only
  GitOps and KubernetesOps storages.
