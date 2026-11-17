# Operational guardrails: step-by-step guide

A registry shared by many teams needs bounds: no 50 MB schemas, no thousand versions of one artifact,
no Confluent API if nobody should use it, no deletes unless someone decides, and a way to freeze
everything during a migration. This guide sets each of these and checks what clients see.

**Time:** about 15 minutes. **You will see:**

- the limits, and what a refused request looks like;
- turning whole APIs off;
- the deletion switches, changed at runtime;
- read-only mode, changed at runtime and followed by every replica;
- why the count limits are per replica today.

The reference is *Operational guardrails* in the Registry documentation
([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc)); the
design is [ADR-0017](../../adr/0017-operational-guardrails.md).

## Before you start

You need Docker with Compose v2 (or Podman), `curl` and `jq`, and free ports 8081 and 8082.

```bash
cd examples/operational-guardrails
./demo.sh
```

`demo.sh` starts PostgreSQL and two replicas from an empty database (the limits count what exists),
runs the steps below, and removes everything. Use `KEEP=1 ./demo.sh` to look around afterwards.

## Step 1: Limits

The replicas run with these limits (all default to `-1`, no limit):

```yaml
APICURIO_LIMITS_CONFIG_MAX_ARTIFACTS: "6"
APICURIO_LIMITS_CONFIG_MAX_VERSIONS_PER_ARTIFACT: "2"
APICURIO_LIMITS_CONFIG_MAX_SCHEMA_SIZE_BYTES: "512"
APICURIO_LIMITS_CONFIG_MAX_ARTIFACT_LABELS: "2"
APICURIO_LIMITS_CONFIG_MAX_LABEL_SIZE_BYTES: "32"
APICURIO_LIMITS_CONFIG_MAX_NAME_LENGTH: "20"
APICURIO_LIMITS_CONFIG_MAX_DESCRIPTION_LENGTH: "40"
```

A request over a limit gets `409 Conflict`, with the limit in the message:

```json
{"title": "Maximum artifact name length exceeded (limit: 20 characters, actual: 27 characters)", "status": 409}
```

`demo.sh` checks each one, that updates are limited too (a long description), and that reads never
are. Limits are read at startup: change them by restarting.

## Step 2: Disabled APIs

```yaml
APICURIO_DISABLE_APIS: "/apis/ccompat/.*,/apis/registry/v2/.*"
```

Each entry is a regular expression matched against the request path; a match answers `404`, as if the
API did not exist. Here the Confluent-compatible API and the v2 API are off; v3 is unaffected. Use it
for APIs nobody should use, not as access control: authentication and authorization are what protect
data.

## Step 3: The deletion switches

Deleting groups, artifacts and versions is off by default; a delete gets `405`. These switches are
**runtime settings**: stored in the database, changed through the admin API, no restart:

```bash
curl -X PUT localhost:8081/apis/registry/v3/admin/config/properties/apicurio.rest.deletion.artifact.enabled \
  -H 'Content-Type: application/json' -d '{"value": "true"}'
```

The other replica picks the change up when it next re-reads the settings
(`apicurio.config.refresh.every`: 1 minute by default, 5 seconds here). `demo.sh` switches deletion on
through replica A and deletes through replica B.

## Step 4: Read-only mode

`apicurio.storage.read-only.enabled` refuses every write with `409`, on every API, while reads keep
working; the console is told (`readOnly: true`, reason `storage`) and hides its write actions. It is a
runtime setting too: switch it on before a migration or a restore, and off afterwards. `demo.sh`
switches it on through A, checks that B follows, and switches it off through B: configuration
changes are still accepted in read-only mode.

Two other things are called read-only and are different: the console setting
`REGISTRY_FEATURE_READ_ONLY` only hides buttons, and the GitOps and KubernetesOps storages are
read-only by nature (see [storage-variants](../storage-variants/)).

## Step 5: Limits and replicas

The artifact and version counts behind `max-artifacts`, `max-total-schemas` and
`max-versions-per-artifact` are counters in each replica's memory, refreshed from the database every
30 seconds. Each replica counts its own writes, so two replicas together can hold up to twice the
limit: `demo.sh` creates artifacts through both replicas and ends with more than 6
([#11218](https://github.com/Apicurio/apicurio-registry/issues/11218)). Treat these limits as a
safety net per replica, not a quota. The size and length limits are exact: they check the request
itself.

`apicurio.limits.config.max-requests-per-second` is not shown: it is accepted but nothing enforces it
([#11219](https://github.com/Apicurio/apicurio-registry/issues/11219)). Rate-limit in your gateway.

## Step 6: Clean up

`demo.sh` removes everything. After `KEEP=1`: `docker compose down -v`.

## Presenter notes

- **The story in one sentence per step:** limits refuse what is too big or too many (1); unused APIs
  can disappear (2); destructive operations are opt-in, at runtime (3); read-only freezes the
  registry without a restart (4); count limits are per replica (5).
- **Questions that usually come up:**
  - *Can teams have different limits?* No: limits are global.
  - *Does read-only stop the console from writing?* Yes: the console uses the same API.

## Troubleshooting

| Symptom | What to do |
|---|---|
| A limit is not applied | Limits are read at startup: restart; `-1` means no limit |
| A runtime switch works on one replica only | Wait for `apicurio.config.refresh.every` (1 minute by default) |
| The cluster holds more artifacts than the limit | Known (#11218): count limits are per replica |
