# v2 to v3 migration: step-by-step guide

Apicurio Registry 3.x cannot read 2.x storage: the database schema and the KafkaSQL journal format
both changed. You migrate by exporting from 2.x and importing into an empty 3.x. This guide does that
with a seeded 2.6 server and checks, entity by entity, that the result is identical.

**Time:** about 20 minutes. **You will see:**

- the documented scripts export from 2.6, import into 3.x and verify it;
- every version keeps its globalId and contentId, so Kafka messages serialized against 2.6 still
  resolve, and clients on the v2 API see the same data;
- how v2 metadata maps to v3, and what changes for clients;
- the guards: no import into a non-empty registry, no reuse of a 2.6 KafkaSQL topic;
- the same export and import, used for backup and restore within 3.x.

For the full procedure (zero-downtime cut-over, client and configuration changes), see the migration
guide in the Registry documentation
([source](../../docs/modules/ROOT/pages/getting-started/assembly-migrating-registry-v2-v3.adoc)).
For how it works, see [ADR-0012](../../adr/0012-import-export-and-migration.md).

## Before you start

You need Docker with Compose v2 (or Podman), `curl`, `jq` and `unzip`, and free ports 8081 to 8084
and 9002.

```bash
cd examples/v2-to-v3-migration
./demo.sh
```

`demo.sh` starts everything, runs the steps below, and removes everything. It ends with
`All checks passed.`. Use `KEEP=1 ./demo.sh` to look around afterwards.

## Step 1: A 2.6 registry with data

`demo.sh` seeds `registry-v2` through the v2 API:

| Artifact | Type | What it exercises |
|---|---|---|
| `common/address`, `orders/customer` (2 versions) | Avro | A reference; version 1 `DEPRECATED`; a `BACKWARD` rule; name, description, a label and a property |
| `common/money`, `payments/payment` | Protobuf | A reference by import |
| `billing/invoice` (`1.0`, `1.1`) | JSON Schema | Custom version names; `1.1` `DISABLED` |
| `orders/orders-api` | OpenAPI (YAML) | A non-schema artifact |
| `legacy`, `legacy-copy` | Avro | No group (`default`); the same content twice, so one contentId |

plus a global `VALIDITY` rule. `demo.sh` records an inventory, one line per version:

```
orders/customer@1 2 2 DEPRECATED 6bfb04a56322 ["common/address@1"]
  coordinates     globalId contentId state  content hash  references
```

## Step 2: Export from 2.6, import into 3.x

With the scripts from [`docs/scripts`](../../docs/scripts/), as the documentation describes:

```bash
REGISTRY_URL=http://localhost:8081 ../../docs/scripts/apicurio-export-registry-data.sh   # GET /apis/registry/v2/admin/export
REGISTRY_URL=http://localhost:8082 ../../docs/scripts/apicurio-import-registry-data.sh   # POST /apis/registry/v3/admin/import
REGISTRY_URL=http://localhost:8082 MANAGEMENT_URL=http://localhost:9002 \
  ../../docs/scripts/apicurio-verify-registry-version.sh
```

The export is a ZIP of JSON entities with a manifest naming the 2.6 server. 3.x reads the manifest,
sees a 1.x/2.x export, and **upgrades** each entity to the v3 model as it imports it. A 3.x export
is imported as is.

3.x serves its health endpoints on the management port (9000), not the API port: pass
`MANAGEMENT_URL` to the verify script.

## Step 3: Parity

`demo.sh` takes the same inventory from `registry-v3` and compares:

- **Every version** with the same globalId, contentId, state, content and references, through the
  v3 API, and again through the **v2 API**, which 3.x still serves at `/apis/registry/v2`.
- **IDs are kept** (`X-Registry-Preserve-GlobalId` and `-ContentId` default to `true`). A Kafka
  message carries the globalId or contentId it was serialized with, so it still finds its schema:
  `demo.sh` fetches 2.6's contentId of `address` from the Confluent-compatible API of 3.x.
- **Rules**, global and per artifact, and they still apply. A rejected change returns `400` in 3.x,
  where 2.x returned `409` (`apicurio.rest.legacy-error-codes.enabled=true` restores `409`).
- **Metadata:** v2 had labels (a list) and properties (a map); v3 has labels (a map). Both become v3
  labels: `{"owner": "team-orders", "pii": null}`. The artifact's name and description come from its
  latest version.
- **Branches:** every artifact gets a `latest` branch with its versions in order, and `latest` still
  skips the `DISABLED` version.

## Step 4: The guards

- `registry-v3` now has data: a second import is refused with `409`, and the import script stops
  before sending it. To add to existing data, use `requireEmptyRegistry=false`
  (`REQUIRE_EMPTY_REGISTRY=false` for the script). An import never replaces data.
- **KafkaSQL:** a 3.x server started on a 2.6 journal topic refuses to start. First because 2.6 made
  the topic compacted, and 3.x requires no compaction or retention; and with that fixed, because the
  records are v2's. Use a new topic for 3.x, and import.

## Step 5: Importing with new IDs

`registry-v3-renum` already has an artifact. `demo.sh` imports the same export with
`requireEmptyRegistry=false`, `X-Registry-Preserve-GlobalId: false` and
`X-Registry-Preserve-ContentId: false`:

- the versions, states, content and references are the same, and references are rewired to the new
  IDs;
- but **no globalId matches 2.6's**: a Kafka message serialized against 2.6 would now resolve to the
  wrong schema, or none. Renumber only to merge registries whose messages you do not need to read.

Known issue: if the target already holds the same content as the export, the renumbered import links
versions to the wrong content ([#11115](https://github.com/Apicurio/apicurio-registry/issues/11115)).

## Step 6: Backup and restore within 3.x

The same APIs are 3.x's backup: `GET /apis/registry/v3/admin/export` from `registry-v3`, then import
into the empty `registry-v3-copy`: identical inventory and rules, and the `DISABLED` version, once
re-enabled, has its content. To import at startup instead (for example, to seed an environment), set
`apicurio.import.url` to the ZIP's URL; readiness waits for it.

## Step 7: Clean up

`demo.sh` removes everything. After `KEEP=1`: `docker compose --profile kafkasql down -v`.

## Presenter notes

- **The story in one sentence per step:** a realistic 2.6 registry (1); one export, one import (2);
  nothing changed, IDs included, through either API (3); you cannot import over data or reuse the old
  topic by mistake (4); renumbering breaks Kafka payloads (5); the same ZIP is your 3.x backup (6).
- **Questions that usually come up:**
  - *Can 3.x read my 2.x database or topic in place?* No: export and import.
  - *Zero downtime?* Run both, make 2.x read-only, import, switch traffic; the documentation describes
    the cut-over.
  - *Confluent Schema Registry?* `utils/exportConfluent` writes the same ZIP format from a Confluent
    registry.

## Troubleshooting

| Symptom | What to do |
|---|---|
| Import returns `409 Registry is not empty` | Import into an empty registry, or use `requireEmptyRegistry=false` |
| 3.x on KafkaSQL does not start: `cleanup.policy=compact` or `records from Apicurio Registry v2` | Give 3.x a new topic (`apicurio.kafkasql.topic`) and import |
| The verify script fails the health check with `404` | Set `MANAGEMENT_URL` to the management port (9000) |
| Clients get `400` instead of `409` for a rule violation | Expected in 3.x; or set `apicurio.rest.legacy-error-codes.enabled=true` |
