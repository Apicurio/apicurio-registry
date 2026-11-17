# Confluent compatibility

Stock Confluent clients and tools against Apicurio Registry's Confluent Schema Registry-compatible
API (`/apis/ccompat/v7`, `/apis/ccompat/v8`), with nothing Apicurio-specific on the client side:

- **Confluent's serdes and console tools** (Avro, Protobuf, JSON Schema) produce and consume through
  the registry; the wire format and schema ID are Confluent's, and the ID is the content ID.
- **Subjects** are artifacts in the default group, or `group:artifact` with group-concat.
- **References**, **compatibility**, **soft and permanent deletes**, **errors**, and **v7 and v8**.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
./demo.sh          # start, check, remove everything
KEEP=1 ./demo.sh   # leave the stack running
```

It ends with `All checks passed.`.

## Services

| Service | Port | Role |
|---|---|---|
| `registry` | 8081 | Apicurio Registry, ccompat defaults |
| `registry-groups` | 8082 | Apicurio Registry with `apicurio.ccompat.group-concat.enabled=true` |
| `kafka` | | Single-node Kafka |
| `confluent` | | Confluent's `cp-schema-registry` image, for its CLI tools only (it runs no Schema Registry) |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | The stack |
| [`demo.sh`](demo.sh) | Runs the Confluent tools and the API calls, and checks the results |
| [`ci.env`](ci.env) | CI settings |

## Notes

- **Known issues**, shown by the demo:
  - [#11124](https://github.com/Apicurio/apicurio-registry/issues/11124): `READONLY` mode is stored
    but not enforced.
  - [#11125](https://github.com/Apicurio/apicurio-registry/issues/11125): some error responses
    differ from Confluent (soft-delete errors return 409, not 404; a class name in the incompatibility
    message; an empty 400 for a subject without the group-concat separator).
- Confluent's and Apicurio's serdes in the same application, both ways: the `confluent` step of
  [`kafka-serdes`](../kafka-serdes/).
- **Local only:** no authentication, no TLS.

## Learn more

- *Confluent Schema Registry compatibility API* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-confluent-schema-registry-compatibility.adoc)).
- [ADR-0001](../../adr/0001-confluent-schema-registry-compatibility.md): how the compatibility layer works.
- Migrating from Confluent Schema Registry: `utils/exportConfluent` and
  [ADR-0012](../../adr/0012-import-export-and-migration.md).
