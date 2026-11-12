# Registry events: step-by-step guide

Apicurio Registry publishes an event to Kafka for every change: a group, artifact or version created,
updated, deleted, a rule configured, a version's state changed. This guide makes a fixed sequence of
changes and reads the events back, on the two storage variants that produce them, and shows what each
one guarantees.

**Time:** about 25 minutes. **You will see:**

- the event types, their keys and payloads, and that dry runs and rejected changes produce none;
- SQL storage: events written to an outbox table in the same transaction as the change, and published
  by Debezium, including after an outage;
- KafkaSQL storage: events published by the registry itself, once, in one total order;
- how to turn the events into CloudEvents.

For the reference documentation, see *Registry events* in the Registry documentation
([source](../../docs/modules/ROOT/pages/getting-started/assembly-registry-events.adoc)) and, for how
they are implemented, [ADR-0011](../../adr/0011-registry-events.md).

## Before you start

You need Docker with Compose v2 (or Podman), `curl` and `jq`, and free ports 8081 to 8083.

```bash
cd examples/registry-events
```

## Step 1: Run both variants

```bash
./demo.sh
```

`demo.sh` starts one variant at a time (Compose profiles `postgresql` and `kafkasql`), makes the
changes, checks the events, and removes everything. It ends with `All checks passed.`. To run one,
and keep it running to look around: `VARIANTS=kafkasql KEEP=1 ./demo.sh`.

## Step 2: The changes, and their events

`demo.sh` makes these changes, and checks that exactly these events are published, in this order:

| Change | Event | Key |
|---|---|---|
| Create group `orders` | `GROUP_CREATED` | `orders` |
| Update its description | `GROUP_METADATA_UPDATED` | `orders` |
| Create `order-placed` with version 1 | `ARTIFACT_CREATED`, `ARTIFACT_VERSION_CREATED` | `orders-order-placed`, `orders-order-placed-1` |
| Add a `BACKWARD` compatibility rule | `ARTIFACT_RULE_CONFIGURED` | `orders-order-placed` |
| A dry run of version 2 | none | |
| A version that breaks the rule (400) | none | |
| Create version 2 | `ARTIFACT_VERSION_CREATED` | `orders-order-placed-2` |
| Update the artifact's name | `ARTIFACT_METADATA_UPDATED` | `orders-order-placed` |
| Deprecate version 1 | `ARTIFACT_VERSION_STATE_CHANGED` | `orders-order-placed-1` |
| Configure a global rule | `GLOBAL_RULE_CONFIGURED` | `VALIDITY` |
| Delete version 1, the artifact, the group | `ARTIFACT_VERSION_DELETED`, `ARTIFACT_DELETED`, `GROUP_DELETED` | |

Only changes that happen produce events: a dry run or a rejected change does not.

Each event is a flat JSON object, keyed by what changed, so a consumer can partition by it:

```json
{"id": "a01b06af-...", "eventType": "GROUP_CREATED", "groupId": "orders"}
{"id": "...", "eventType": "ARTIFACT_VERSION_STATE_CHANGED", "groupId": "orders", "artifactId": "order-placed",
 "version": "1", "oldState": "ENABLED", "newState": "DEPRECATED"}
```

There are 16 event types; contract changes have their own. The documentation lists every payload.

## Step 3: SQL storage: the outbox

On PostgreSQL (and SQL Server), the registry writes each event as a row of the table `outbox` **in
the same transaction as the change**, and deletes it in that transaction. The table stays empty, but
the database's write-ahead log has the row, and Debezium reads the log.

The Compose profile `postgresql` runs PostgreSQL with `wal_level=logical`, Kafka, and Kafka Connect
with Debezium. `demo.sh` registers [`connect/outbox-connector.json`](connect/outbox-connector.json):
the PostgreSQL connector, limited to `public.outbox`, with Debezium's **outbox event router**, which
turns each row into a record on `outbox.event.registry-events`, keyed by the changed coordinates.

Read them yourself (with `KEEP=1`):

```bash
docker compose --profile postgresql exec kafka-pg /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic outbox.event.registry-events --from-beginning --property print.key=true
```

What this gives you:

- **An event if, and only if, the change was committed.** A failed request writes nothing.
- **At least once.** `demo.sh` stops Kafka Connect, makes a change (it succeeds), checks that no event
  is published, starts Kafka Connect again, and checks that the event arrives: Debezium resumes from
  the log. A consumer should use the event `id` to ignore duplicates.
- **Commit order.**

One difference: when an artifact is created with its first version, `ARTIFACT_VERSION_CREATED` comes
before `ARTIFACT_CREATED` on SQL storage ([#11097](https://github.com/Apicurio/apicurio-registry/issues/11097)).

H2 and MySQL produce no events.

## Step 4: KafkaSQL storage

With KafkaSQL, the registry publishes each event itself to `registry-events`, once the change has
been applied, from the replica that handled the request:

- the same events and payloads, on one partition, so in **one total order**;
- **once**: the profile runs two replicas, and a change through the second one is published once,
  not once per replica;
- **best effort**: publishing is not part of the change. If it fails, the change is kept and the event
  is lost. Use SQL storage when every event must be delivered.

## Step 5: Consuming events

Any Kafka consumer works. A typical consumer:

1. reads `outbox.event.registry-events` (SQL) or `registry-events` (KafkaSQL);
2. skips event `id`s it has already seen;
3. acts on the `eventType`: refresh a cache on `ARTIFACT_VERSION_CREATED`, alert on
   `ARTIFACT_VERSION_STATE_CHANGED` to `DEPRECATED`, audit everything.

There is no HTTP webhook delivery.

## Step 6: GitOps and KubernetesOps

They produce no events: their changes are commits and ConfigMap updates, which you can already watch.

## Step 7: CloudEvents

The events are not CloudEvents yet: the payload is published as is. The registry reserves the
CloudEvents type names `io.apicurio.registry.<what>.<change>`
([#9324](https://github.com/Apicurio/apicurio-registry/issues/9324),
[#8426](https://github.com/Apicurio/apicurio-registry/issues/8426)). Until it publishes them,
[`to-cloudevents.sh`](to-cloudevents.sh) converts events to CloudEvents 1.0 (structured mode);
`demo.sh` checks its output:

```json
{"specversion": "1.0", "id": "...", "source": "/apicurio-registry",
 "type": "io.apicurio.registry.artifact.version.created", "subject": "orders/order-placed/1",
 "datacontenttype": "application/json", "data": {...the event...}}
```

## Step 8: Clean up

`demo.sh` removes everything. After `KEEP=1`: `docker compose --profile '*' down -v`.

## Presenter notes

- **The story in one sentence per step:** every real change is an event, keyed by what changed (2); the
  outbox makes it transactional and survives outages (3); KafkaSQL is simpler, ordered and best effort
  (4); consumers deduplicate by id (5); CloudEvents are a small step away (7).
- **Questions that usually come up:**
  - *Why Debezium and not the registry publishing directly on SQL storage?* Publishing directly cannot
    be atomic with the database commit; the outbox can.
  - *Do I get the schema content in the event?* No, the coordinates: fetch the content from the
    registry.

## Troubleshooting

| Symptom | What to do |
|---|---|
| No events on `outbox.event.registry-events` | The connector must be `RUNNING` (`curl localhost:8083/connectors/registry-outbox/status`), and PostgreSQL needs `wal_level=logical`. Changes made before the connector started are not captured. |
| No events with SQL storage | Only PostgreSQL and SQL Server produce events. |
| Events appear twice | At-least-once delivery: deduplicate by `id`. |
