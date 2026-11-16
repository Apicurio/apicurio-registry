# Schema references

A complete, local example of artifact references in Apicurio Registry: schemas that use types
defined in other schemas.

- **The Maven plugin** registers Avro, JSON Schema and AsyncAPI schemas with their references,
  spelled out or found automatically (`autoRefs`).
- **The REST API** lists outbound and inbound references and the reference graph, and returns content
  as stored, dereferenced or rewritten.
- **Integrity rules**, canonical matching, and what disabling or deleting a referenced artifact does.
- **Kafka serializers** register nested Avro types and Protobuf imports as references, and the
  deserializers resolve them.

**New to this? Follow the [step-by-step guide](GUIDE.md)** (about 30 minutes).

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq` and JDK 21 or later.

```bash
docker compose up -d     # starts Kafka, the Registry and its UI
./demo.sh                # registers the schemas, then checks every behaviour
                         # (its first run builds the Maven plugin and the client: a few minutes)
```

`demo.sh` prints one line per check and ends with `All checks passed.`. Then open the UI at
<http://localhost:8888> and look at the **References** tab of `orders-manual/Order`.

Stop and remove everything with `docker compose down -v`.

## Services

| Service               | URL                   | Purpose                     |
|-----------------------|-----------------------|-----------------------------|
| Kafka                 | localhost:9092        | Broker for the serdes step  |
| Apicurio Registry API | http://localhost:8081 | Registry, in-memory storage |
| Apicurio Registry UI  | http://localhost:8888 | Web console                 |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`images/`](images) | The guide's UI screenshots; [`capture-screenshots.cjs`](capture-screenshots.cjs) refreshes them |
| [`registration/`](registration) | The schemas, and the Maven plugin executions that register them |
| [`serdes/`](serdes) | Kafka client sending Avro and Protobuf records with nested types |
| [`build.sh`](build.sh) | Builds the Maven plugin and the serdes client from this checkout |
| [`demo.sh`](demo.sh) | Resets the data, registers the schemas, and checks every behaviour |
| [`demo-env.sh`](demo-env.sh) | Shell helpers (`status`, `call`) to call the API by hand |
| [`ci.env`](ci.env) | CI settings: build the Java parts before `demo.sh` |

## Notes

- **Known issues:** deleting a referenced artifact is allowed and breaks its referrers
  ([#11029](https://github.com/Apicurio/apicurio-registry/issues/11029)); a cycle closed by a new
  artifact is not caught, and dereferencing a cycle fails
  ([#11030](https://github.com/Apicurio/apicurio-registry/issues/11030)). The guide shows both.
- **Local build of the registry:** start the example with `REGISTRY_IMAGE=<image> docker compose up -d`.
- **Local only:** in-memory storage, no authentication, a single-node broker without security.

## Learn more

- *Artifact references* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-artifact-reference.adoc)).
- [ADR-0008](../../adr/0008-artifact-references.md): how references are implemented.
- [Kafka serdes](../kafka-serdes/) and [Rules and compatibility](../rules-and-compatibility/).
