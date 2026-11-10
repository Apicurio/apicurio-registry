# Kafka serdes

A complete, local example of the Apicurio Registry serializers and deserializers (serdes) for Kafka:

- **Kafka** (single-node KRaft broker), **Apicurio Registry** and its UI, in Docker Compose.
- **A Java tour** of producers and consumers using the Avro, JSON Schema and Protobuf serdes:
  auto-registration, the wire format and headers mode, generated and plain Java classes, naming
  strategies (built-in and custom), a custom resolver, schema evolution, a rule rejecting a schema,
  and a schema registered by the build with the Maven plugin.
- **Kafka Connect** with the Apicurio Avro converter, in a sink and a source connector.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It explains every step, the properties
it uses and what to look for, in about 40 minutes.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq` and JDK 21 or later.

```bash
docker compose up -d     # starts Kafka, the Registry and its UI
./demo.sh                # runs the tour and Kafka Connect, then checks the results
                         # (its first run builds the tour from this checkout: a few minutes)
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a check
fails). Then open the UI at <http://localhost:8888>.

Stop and remove everything with `docker compose --profile connect down -v`.

## Services

| Service               | URL                   | Purpose                                         |
|-----------------------|-----------------------|-------------------------------------------------|
| Kafka                 | localhost:9092        | Broker; `kafka:29092` inside the Compose network |
| Apicurio Registry API | http://localhost:8081 | Registry, in-memory storage                     |
| Apicurio Registry UI  | http://localhost:8888 | Web console                                     |
| Kafka Connect         | http://localhost:8083 | Profile `connect`, started by `demo.sh` (step 13) |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`images/`](images) | The guide's UI screenshots; [`capture-screenshots.cjs`](capture-screenshots.cjs) refreshes them |
| [`docker-compose.yml`](docker-compose.yml) | The services |
| [`tour/`](tour) | The Java producers and consumers ([`Tour.java`](tour/src/main/java/io/apicurio/registry/examples/serdes/Tour.java)), their schemas, and the Maven plugin configuration |
| [`connect/worker.properties`](connect/worker.properties) | The Kafka Connect worker configuration |
| [`connect-plugin/`](connect-plugin) | Collects the Apicurio converters into the worker's plugin directory |
| [`build.sh`](build.sh) | Builds the tour, the Maven plugin and the converters from this checkout |
| [`demo.sh`](demo.sh) | Resets the data, runs the tour and the connectors, and checks the results |
| [`ci.env`](ci.env) | CI settings: build the Java parts before `demo.sh` |

## Notes

- **Other ports:** see [Using other ports](GUIDE.md#using-other-ports).
- **Built from the checkout:** the tour uses the serdes from this repository, so it shows the current
  code. Snapshots are not published, which is why `build.sh` builds them.
- **Local build of the registry:** the example uses the `latest-snapshot` images; to run a locally
  built registry, start the example with `REGISTRY_IMAGE=<image> docker compose up -d`.
- **Known issues:** a rule violation reaches the producer as a `RuntimeException` with no message
  ([#11018](https://github.com/Apicurio/apicurio-registry/issues/11018)); the guide shows how to read
  the reason.
- **Local only:** in-memory storage, no authentication, a single-node broker without security.

## Learn more

- *Configuring Kafka serializers/deserializers* and *Using Kafka client serializers/deserializers* in
  the Registry documentation ([source](../../docs/modules/ROOT/pages/getting-started/)).
- [ADR-0007](../../adr/0007-kafka-serdes.md): how the serdes and the schema resolver work.
- [Rules and compatibility](../rules-and-compatibility/): the rules that step 10 runs into.
