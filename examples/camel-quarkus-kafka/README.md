# Camel Quarkus Kafka with Apicurio Registry

A complete, local example of Apache Camel Quarkus routes that send and receive Avro records through
Kafka with the Apicurio Registry serdes, configured only in `application.properties`:

- **A Camel producer route** (a timer, and an HTTP endpoint) sends `Greeting` records to the
  `greetings` topic. The Avro serializer registers the schema as `default/greetings-value` and writes
  its content ID in front of every record.
- **A Camel consumer route** deserializes them with the Avro deserializer, which fetches the schema
  by that ID. A second consumer route reads the same records as raw bytes, to show the wire format.
- **Apicurio Registry** stores the schema and enforces a compatibility rule on its evolution.
- **Apache Kafka** (a single KRaft broker) carries the records.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It walks through the routes, the
registered schema, the bytes on the wire, and a schema change, in about 20 minutes.

Unlike [`kafka-serdes`](../kafka-serdes/), which uses the Kafka client API directly, this example
shows the serdes behind the Camel Kafka component: there is no Kafka client code at all.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq` and JDK 21 or later.

```bash
./build.sh               # builds the application and the serdes from this checkout (a few minutes)
docker compose up -d     # starts Kafka, the Registry and its UI
./demo.sh                # starts the application, then checks the expected behaviour
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a check
fails). It starts the application on port 8090 and stops it at the end; its log is
`target/camel-app.log`. Then open the UI at <http://localhost:8888>.

Stop and remove everything with `docker compose down -v`.

## Services

| Service               | URL                   | Purpose                                         |
|-----------------------|-----------------------|-------------------------------------------------|
| Apache Kafka          | localhost:9092        | The `greetings` topic                           |
| Apicurio Registry API | http://localhost:8081 | Stores the `greetings-value` schema             |
| Apicurio Registry UI  | http://localhost:8888 | Web console                                     |
| Camel application     | http://localhost:8090 | Runs on the host; `/greetings/send`, `/received`, `/raw` |

## Configuration

All the serde configuration is in
[`src/main/resources/application.properties`](src/main/resources/application.properties):

| Property | Value | Meaning |
|----------|-------|---------|
| `camel.component.kafka.value-serializer` | `io.apicurio.registry.serde.avro.AvroKafkaSerializer` | Avro serializer for every Kafka endpoint |
| `camel.component.kafka.value-deserializer` | `io.apicurio.registry.serde.avro.AvroKafkaDeserializer` | Avro deserializer for every Kafka endpoint |
| `camel.component.kafka.additional-properties[apicurio.registry.url]` | `http://localhost:8081/apis/registry/v3` | Registry API (`REGISTRY_URL` overrides it) |
| `...additional-properties[apicurio.registry.auto-register]` | `true` | The serializer registers the schema if it is missing |
| `...additional-properties[apicurio.registry.auto-register.if-exists]` | `FIND_OR_CREATE_VERSION` | If the artifact exists, reuse the version with the same content, or add one |
| `...additional-properties[apicurio.registry.avro-datum-provider]` | `io.apicurio.registry.serde.avro.ReflectAvroDatumProvider` | How the serdes read and write the Java objects |

`additional-properties[...]` passes each entry through the Camel Kafka component to the Kafka
producer and consumer, where the serdes read it. Anything not set keeps the serde default: the
artifact is named by `TopicIdStrategy` (`<topic>-value`, in group `default`), and the record carries
a magic byte and the 4-byte **content ID** (`apicurio.registry.use-id=contentId`), not headers
(`apicurio.registry.headers.enabled=false`). A Kafka endpoint can override a component setting; the
raw consumer route does that with `valueDeserializer=...ByteArrayDeserializer`.

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | Kafka, the Registry and its UI |
| [`build.sh`](build.sh) | Builds the application and the Avro serde from this checkout |
| [`demo.sh`](demo.sh) | Starts the application and checks the expected behaviour |
| [`src/main/java/.../GreetingRouteBuilder.java`](src/main/java/io/apicurio/registry/examples/camelkafka/GreetingRouteBuilder.java) | The Camel routes |
| [`src/main/resources/avro/greeting.avsc`](src/main/resources/avro/greeting.avsc) | The schema; the `Greeting` class is generated from it |
| [`schemas/`](schemas) | Two changes to the schema: one compatible, one not |

## Notes

- **Serdes from this checkout:** snapshots of the serdes are not published, so `pom.xml` depends on
  `apicurio-registry-avro-serde-kafka` at this checkout's version, next to the
  `quarkus-apicurio-registry-avro` extension (which would otherwise bring the release pinned by the
  Quarkus BOM).
- **Other ports:** see [Using other ports](GUIDE.md#using-other-ports).
- **Local only:** no authentication and in-memory storage (data is lost on restart).

## Learn more

- *Configuring Kafka serializers/deserializers* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-kafka-client-serdes.adoc)).
- [ADR-0007](../../adr/0007-kafka-serdes.md): how the serdes are implemented.
- [Camel Quarkus Kafka](https://camel.apache.org/camel-quarkus/latest/reference/extensions/kafka.html).
