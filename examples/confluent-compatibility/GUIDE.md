# Confluent compatibility: step-by-step guide

Apicurio Registry serves a Confluent Schema Registry-compatible API, so applications built for
Confluent Schema Registry can switch by changing one URL. This guide runs Confluent's own serializers
and console tools against it, and checks what they see, including the places where the registry's own
model shows through.

**Time:** about 20 minutes. **You will see:**

- Confluent's Avro, Protobuf and JSON Schema serdes, unchanged, with `schema.registry.url` set to
  the registry;
- how subjects map to Apicurio artifacts and groups;
- references, compatibility rules, soft and permanent deletes, and errors;
- what differs between the v7 and v8 APIs, and what does not work yet.

For the reference documentation, see *Confluent Schema Registry compatibility API* in the Registry
documentation
([source](../../docs/modules/ROOT/pages/getting-started/assembly-confluent-schema-registry-compatibility.adoc)).
For how it works, see [ADR-0001](../../adr/0001-confluent-schema-registry-compatibility.md).

## Before you start

You need Docker with Compose v2 (or Podman), `curl` and `jq`, and free ports 8081 and 8082. The
Confluent image is large (about 1 GB) the first time.

```bash
cd examples/confluent-compatibility
./demo.sh
```

`demo.sh` starts the stack from empty, runs every step below, and removes it. It ends with
`All checks passed.`. Use `KEEP=1 ./demo.sh` to look around afterwards.

## Step 1: Confluent's serdes, unchanged

The `confluent` service is Confluent's `cp-schema-registry` image, used for its console tools only.
They point at the registry:

```bash
docker compose exec confluent kafka-avro-console-producer --bootstrap-server kafka:9092 --topic orders \
  --property schema.registry.url=http://registry:8080/apis/ccompat/v7 \
  --property value.schema='{"type":"record","name":"OrderPlaced",...}'
```

- The producer registers `orders-value` (Confluent's `TopicNameStrategy`) and sends; the consumer,
  pointed at `/apis/ccompat/v8`, reads it back. Both API versions serve the same data.
- Each record starts with a magic byte `0` and a 4-byte schema ID: Confluent's wire format. **The ID
  is the content ID** of the version in Apicurio (`GET /apis/registry/v3/groups/default/artifacts/orders-value/versions/1`).
- The Protobuf and JSON Schema console tools work the same way; each subject becomes an artifact of
  the matching type (`AVRO`, `PROTOBUF`, `JSON`).

To mix Confluent's and Apicurio's serdes in the same system, see the `confluent` step of
[`kafka-serdes`](../kafka-serdes/): each reads what the other wrote.

## Step 2: Subjects, artifacts and groups

Confluent has subjects; Apicurio has groups of artifacts.

- By default, **a subject is an artifact in the `default` group**, with the subject as the artifact
  ID.
- Registering the same schema under another subject returns **the same ID**, as Confluent does,
  because the ID is the content ID. (`apicurio.ccompat.legacy-id-mode.enabled=true` uses the global
  ID instead: one ID per registration.)
- Artifacts in other groups are not subjects, unless the client sends `X-Registry-GroupId`.
- With `apicurio.ccompat.group-concat.enabled=true` (`registry-groups`), the subject
  `orders:order-placed` is the artifact `order-placed` in the group `orders`, and every artifact is
  listed as `group:artifact`. The separator is `apicurio.ccompat.group-concat.separator`.

## Step 3: References

A schema registered with `references` (`{"name": "com.example.Address", "subject": "address",
"version": 1}`) keeps them, and they are Apicurio artifact references in the v3 API. A Confluent
producer configured with `value.schema.id` and `auto.register.schemas=false` uses it, and the
consumer resolves the reference.

## Step 4: Compatibility

- With no rule configured, the **check** endpoint
  (`POST /compatibility/subjects/{subject}/versions/latest`) applies `BACKWARD`, as Confluent does.
  **Registration does not**: without a rule, any schema is accepted. Configure the level explicitly.
- `PUT /config/{subject}` with `BACKWARD` creates the Apicurio `COMPATIBILITY` rule of that artifact.
  An incompatible schema is then rejected with `409` (`error_code` 409), and the Confluent producer
  fails to send.
- It is the same rule: changing it through the Apicurio API changes `GET /config/{subject}`.

## Step 5: Soft and permanent deletes

Confluent deletes in two stages; Apicurio uses version states:

| Confluent | Apicurio |
|---|---|
| `DELETE /subjects/{s}/versions/{v}` (soft) | the version's state becomes `DISABLED` |
| listed with `?deleted=true` | `DISABLED` versions are included |
| `DELETE ...?permanent=true` | the version is deleted; only after a soft delete (`40407` otherwise) |
| `DELETE /subjects/{s}` then `?permanent=true` | every version disabled, then the artifact deleted |

A second soft delete returns `40406`. The HTTP status of `40405`, `40406` and `40407` is `409`, where
Confluent returns `404` ([#11125](https://github.com/Apicurio/apicurio-registry/issues/11125)).

## Step 6: Errors

Errors have Confluent's shape and codes: `40401` subject not found, `40402` version not found,
`40403` schema not found, `42201` invalid schema, `42203` invalid compatibility level.

## Step 7: v7 and v8, and what does not work yet

- v8 is the same implementation as v7: the same subjects, schema types, config and versions. One
  difference: v8 includes an empty `references` list in a schema; v7 leaves it out.
- **Modes are stored but not enforced:** `PUT /mode/{subject}` with `READONLY` is returned by
  `GET /mode/{subject}`, but writes still succeed. Confluent rejects them (`422`, `42205`)
  ([#11124](https://github.com/Apicurio/apicurio-registry/issues/11124)). Do not rely on `READONLY` to
  freeze a registry during a migration; use Apicurio's read-only mode
  (`apicurio.storage.read-only.enabled`) instead.
- Not supported at all: schema linking (exporters), KEKs and DEKs, the metadata API, and the
  enforcement of Confluent data contract rules. See the documentation.

## Step 8: Clean up

`demo.sh` removes everything. After `KEEP=1`: `docker compose down -v`.

## Presenter notes

- **The story in one sentence per step:** Confluent's own tools just work, and the ID is the content
  ID (1); a subject is an artifact, groups are opt-in (2); references (3) and compatibility (4) map to
  Apicurio's; deletes are states (5); errors look like Confluent's (6); v7 and v8 are one
  implementation, and modes are not enforced yet (7).
- **Questions that usually come up:**
  - *Do I have to change my producers?* Only `schema.registry.url`.
  - *Do my existing schema IDs survive a migration from Confluent?* Yes, with `utils/exportConfluent`
    and an import (see [ADR-0012](../../adr/0012-import-export-and-migration.md)).
  - *Why is the check `BACKWARD` but registration not?* The check imitates Confluent's default; the
    registry only enforces configured rules (ADR-0001, limitation 2).

## Troubleshooting

| Symptom | What to do |
|---|---|
| `Subject 'x' not found` for an artifact you can see in the UI | It is not in the `default` group: send `X-Registry-GroupId`, or enable group-concat |
| Incompatible schemas are accepted | Configure a compatibility level: `PUT /config` or `PUT /config/{subject}` |
| A client expects `404` for a soft-delete error | Known difference (#11125): check `error_code` instead |
| `READONLY` does not block writes | Known issue (#11124): use `apicurio.storage.read-only.enabled` |
