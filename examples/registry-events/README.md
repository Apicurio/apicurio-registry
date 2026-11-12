# Registry events

The events Apicurio Registry publishes to Kafka for every change, read back and checked on the two
storage variants that produce them:

- **SQL storage (PostgreSQL):** events written to an outbox table in the same transaction as the
  change, published by Debezium (Kafka Connect), delivered after an outage.
- **KafkaSQL storage:** events published by the registry, once, in one total order, with two replicas.
- **CloudEvents:** [`to-cloudevents.sh`](to-cloudevents.sh) converts the events to CloudEvents 1.0.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
./demo.sh                          # both variants, one after another
VARIANTS=kafkasql KEEP=1 ./demo.sh # one, left running
```

`demo.sh` makes a fixed sequence of changes, then checks the exact events, keys and payloads, and
ends with `All checks passed.`.

## Services

| Profile | Services | Topic |
|---|---|---|
| `postgresql` | `postgres` (logical WAL), `kafka-pg`, `connect` (Debezium, 8083), `registry-pg` (8081) | `outbox.event.registry-events` |
| `kafkasql` | `kafka-ks`, `registry-ks-a` (8081), `registry-ks-b` (8082) | `registry-events` |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | Both variants, one profile each |
| [`connect/outbox-connector.json`](connect/outbox-connector.json) | The Debezium connector: the outbox table, with the outbox event router |
| [`to-cloudevents.sh`](to-cloudevents.sh) | Converts events to CloudEvents 1.0 (structured mode) |
| [`demo.sh`](demo.sh) | Makes the changes and checks the events |
| [`ci.env`](ci.env) | CI settings: one job per variant |

## Notes

- **Known issue:** on SQL storage, `ARTIFACT_VERSION_CREATED` comes before `ARTIFACT_CREATED` when an
  artifact is created with its first version ([#11097](https://github.com/Apicurio/apicurio-registry/issues/11097)).
- Events are produced by PostgreSQL and SQL Server (SQL storage) and by KafkaSQL; not by H2, MySQL,
  GitOps or KubernetesOps.
- **Local only:** default passwords, no TLS, single-node Kafka.

## Learn more

- *Registry events* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-registry-events.adoc)).
- [ADR-0011](../../adr/0011-registry-events.md): how events are implemented.
- [Storage variants](../storage-variants/) and [ADR-0010](../../adr/0010-storage-architecture.md).
