# v2 to v3 migration

Migrates an Apicurio Registry 2.6 server to 3.x with the documented export and import scripts, and
proves that nothing changed:

- **Parity:** every version, with its globalId, contentId, state, content and references, the
  metadata and the rules, through the v3 API and through the v2 API that 3.x still serves.
- **IDs:** kept by default, so Kafka messages serialized against 2.6 still resolve; what happens when
  they are not.
- **Guards:** a registry that is not empty refuses an import; 3.x refuses a 2.6 KafkaSQL topic.
- **Backup and restore within 3.x:** export, import into an empty server, identical.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq` and `unzip`.

```bash
./demo.sh          # seed, migrate, check, remove everything
KEEP=1 ./demo.sh   # leave the servers running
```

It ends with `All checks passed.`.

## Services

| Service | Port | Role |
|---|---|---|
| `registry-v2` | 8081 | 2.6, in memory: the source, seeded by `demo.sh` |
| `registry-v3` | 8082 (management 9002) | 3.x: the target, IDs kept |
| `registry-v3-renum` | 8083 | 3.x with data already: imported with new IDs |
| `registry-v3-copy` | 8084 | 3.x: restored from a v3 export of `registry-v3` |
| `kafka`, `registry-v2-kafkasql`, `registry-v3-kafkasql` | | Profile `kafkasql`: 3.x pointed at a 2.6 journal topic |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | The servers |
| [`seed/`](seed/) | The schemas and API seeded into 2.6: Avro and Protobuf with references, JSON Schema, OpenAPI |
| [`demo.sh`](demo.sh) | Seeds, migrates with [`docs/scripts`](../../docs/scripts/), and checks |
| [`ci.env`](ci.env) | CI settings |

## Notes

- **Known issues**, worked around:
  - [#11115](https://github.com/Apicurio/apicurio-registry/issues/11115): an import with
    `X-Registry-Preserve-ContentId: false` links versions to the wrong content when the target already
    holds the same content. The demo's existing artifact uses content that is not in the export.
  - [#11116](https://github.com/Apicurio/apicurio-registry/issues/11116): the v2 API on 3.x serves a
    `DISABLED` version's content by globalId; 2.6 did not. The demo does not compare that content.
- **Local only:** no authentication, no TLS.

## Learn more

- *Migrating from Apicurio Registry 2.x to 3.x* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-migrating-registry-v2-v3.adoc)).
- [ADR-0012](../../adr/0012-import-export-and-migration.md): how import, export and the upgrade work.
