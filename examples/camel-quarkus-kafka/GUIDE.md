# Camel Quarkus Kafka: step-by-step guide

This guide walks through Camel Quarkus routes that exchange Avro records through Kafka with the
Apicurio Registry serdes. You will see what the serializer registers, what it puts on the wire, how
the consumer finds the schema again, and what a compatibility rule does to a schema change. Follow it
on your own, or use it as the script for a live demo. Run the steps in order.

**Time:** about 20 minutes. **You will see:**

- serdes configured for Camel entirely in `application.properties`;
- the schema registered on the first record, as `default/greetings-value`;
- the 5-byte prefix (magic byte and content ID) in front of every record;
- the consumer route deserializing the records into `Greeting` objects;
- a compatible schema change accepted, an incompatible one refused, and the running producer
  unaffected.

For the reference documentation, see *Configuring Kafka serializers/deserializers* in the Registry
documentation ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-kafka-client-serdes.adoc))
and, for how the serdes are implemented, [ADR-0007](../../adr/0007-kafka-serdes.md).

## Before you start

You need:

- Docker with Docker Compose v2, or Podman with a Compose provider;
- `curl` and `jq`;
- JDK 21 or later: the application and the serdes are built from this checkout (serde snapshots are
  not published);
- a shell (bash or zsh), and a browser for the UI;
- free ports 9092 (Kafka), 8081 (Registry API), 8888 (Registry UI) and 8090 (the application). If
  they are in use, see [Using other ports](#using-other-ports).

Run every command from this directory:

```bash
cd examples/camel-quarkus-kafka
```

## Step 1: Build and start the example

```bash
./build.sh
docker compose up -d
docker compose ps --format '{{.Name}}: {{.Status}}'
```

```
kafka-camel: Up ... (healthy)
apicurio-registry-api-camel: Up ... (healthy)
apicurio-registry-ui-camel: Up ...
```

`build.sh` installs the Avro serde from this checkout and packages the application as
`target/quarkus-app/quarkus-run.jar`.

## Step 2: Check everything works

```bash
./demo.sh
```

It starts the application, runs every step below, stops the application and ends with:

```
All checks passed.
```

It is safe to run again: it deletes the `greetings-value` artifact and recreates the `greetings`
topic first. For the steps below, restart the stack so you start from nothing, then run the
application yourself, in a second terminal:

```bash
docker compose down -v && docker compose up -d --wait
java -jar target/quarkus-app/quarkus-run.jar
```

(or `../../mvnw quarkus:dev` for live reload).

## Step 3: The routes and their configuration

Open [`GreetingRouteBuilder.java`](src/main/java/io/apicurio/registry/examples/camelkafka/GreetingRouteBuilder.java).
There are four routes that touch Kafka, and none of them mentions a serializer:

- `timer:greeting` creates a `Greeting` every 5 seconds and sends it to `kafka:greetings`;
- `platform-http:/greetings/send` does the same with a name and message you choose;
- `kafka:greetings?groupId=camel-greeting-consumer` receives `Greeting` objects;
- `kafka:greetings?groupId=camel-greeting-raw&valueDeserializer=...ByteArrayDeserializer` receives
  the same records as bytes: an endpoint option overrides the component setting.

The serdes are set once, for the component, in
[`application.properties`](src/main/resources/application.properties):
`camel.component.kafka.value-serializer`, `value-deserializer`, and the registry settings in
`camel.component.kafka.additional-properties[apicurio.registry.*]`, which Camel passes to the Kafka
producer and consumer unchanged. The [README](README.md#configuration) lists them.

In the application log:

```
Sending greeting for Alice: Hello from Camel!
Received greeting — name: Alice, message: Hello from Camel!, timestamp: 1763...
```

## Step 4: The schema the serializer registered

The first record made the serializer register the schema (`apicurio.registry.auto-register=true`).
The artifact is named by the default `TopicIdStrategy`: `<topic>-value`, in group `default`.

```bash
curl -s http://localhost:8081/apis/registry/v3/groups/default/artifacts/greetings-value/versions/1 \
  | jq '{artifactType, version, globalId, contentId}'
```

```json
{
  "artifactType": "AVRO",
  "version": "1",
  "globalId": 1,
  "contentId": 1
}
```

The content is [`greeting.avsc`](src/main/resources/avro/greeting.avsc):

```bash
curl -s http://localhost:8081/apis/registry/v3/groups/default/artifacts/greetings-value/versions/1/content \
  | jq -c '[.name, [.fields[].name]]'
```

```
["Greeting",["name","message","timestamp"]]
```

**In the UI:** open <http://localhost:8888>, then the `default` group and `greetings-value`.

## Step 5: Send a greeting and read it back

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST 'http://localhost:8090/greetings/send?name=Ada&message=Hi'
curl -s http://localhost:8090/greetings/received | jq -c '.[] | select(.name == "Ada")'
```

```
204
{"name":"Ada","message":"Hi","timestamp":1763...}
```

The consumer route got a `Greeting` back: the deserializer read the ID in front of the record,
fetched the schema with it, and decoded the Avro payload. The raw route shows that ID:

```bash
curl -s http://localhost:8090/greetings/raw | jq -c '.[] | select(.key == "Ada")'
```

```
{"key":"Ada","magicByte":0,"schemaId":1,"length":...}
```

A magic byte `0`, then the 4-byte **content ID** of version 1 (`apicurio.registry.use-id` defaults
to `contentId`). The ID is the content's, not the version's: after `demo.sh` has run a few times, the
global ID of version 1 grows while its content ID stays 1. The deserializer looks it up with:

```bash
curl -s http://localhost:8081/apis/registry/v3/ids/contentIds/1 | jq -r .name
```

```
Greeting
```

## Step 6: Change the schema under a compatibility rule

Add a `BACKWARD` compatibility rule: a new version must be able to read data written with the
previous one.

```bash
A=http://localhost:8081/apis/registry/v3/groups/default/artifacts/greetings-value
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$A/rules" -H 'Content-Type: application/json' \
  -d '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}'
```

```
204
```

[`schemas/greeting-v2-incompatible.avsc`](schemas/greeting-v2-incompatible.avsc) adds a `language`
field without a default, so it cannot read the existing records:

```bash
new_version() { jq -nc --rawfile s "$1" '{content: {content: $s, contentType: "application/json"}}' \
  | curl -s -X POST "$A/versions" -H 'Content-Type: application/json' -d @-; }
new_version schemas/greeting-v2-incompatible.avsc | jq '{status, ruleType: .ruleViolation.ruleType}'
```

```json
{
  "status": 400,
  "ruleType": "COMPATIBILITY"
}
```

[`schemas/greeting-v2.avsc`](schemas/greeting-v2.avsc) gives it the default `"en"`, and is accepted
as version 2:

```bash
new_version schemas/greeting-v2.avsc | jq -r .version
```

```
2
```

The running application still sends the original `Greeting`. With
`apicurio.registry.auto-register.if-exists=FIND_OR_CREATE_VERSION`, the serializer finds the version
with that exact content (version 1) instead of creating a third one:

```bash
curl -s -o /dev/null -X POST 'http://localhost:8090/greetings/send?name=Grace&message=Again'
curl -s http://localhost:8090/greetings/raw | jq -c '.[] | select(.key == "Grace") | [.magicByte, .schemaId]'
curl -s "$A/versions" | jq -c '[.versions[].version]'
```

```
[0,1]
["1","2"]
```

## Step 7: Clean up

Stop the application (Ctrl+C), then:

```bash
docker compose down -v
```

## Presenter notes

- **Before the session:** run `./build.sh`, `docker compose up -d` and `./demo.sh`, so the images
  and Maven dependencies are downloaded.
- **The story in one sentence per step:** Camel needs no serde code, only properties (3); the first
  record registers the schema (4); every record carries five bytes that point at it (5); the
  registry, not the producer, decides which schema changes are allowed (6).
- **Questions that usually come up:**
  - *Can I put the ID in headers instead?* Yes: add
    `camel.component.kafka.additional-properties[apicurio.registry.headers.enabled]=true`. See
    [`kafka-serdes`](../kafka-serdes/GUIDE.md).
  - *Why does the producer not use version 2?* It serializes with the schema of its generated
    class; regenerate `Greeting` from the new schema to use it.
  - *Can a single route use a different serde?* Yes: endpoint options (`valueSerializer`,
    `valueDeserializer`) override the component's, as the raw route shows.
- **To reset between runs:** `./demo.sh` resets the topic and the artifact itself.

## Troubleshooting

| Symptom | What to do |
|---|---|
| `demo.sh` times out waiting for the Registry | Check `docker logs apicurio-registry-api-camel`. Usually a port conflict: see [Using other ports](#using-other-ports). |
| `demo.sh` times out waiting for the Camel application | Read the end of `target/camel-app.log`. Port 8090 may be in use: set `APP_PORT`. |
| `build.sh` fails | Check `java -version` (21 or later). The first build downloads many dependencies. |
| No greetings are received | Check the brokers in the application log (`localhost:9092` by default) and `docker logs kafka-camel`. |
| `Field ... does not accept null values` | The Avro record was built without a required field; every `Greeting` needs `name`, `message` and `timestamp`. |

## Using other ports

Choose free ports, and tell Compose, `demo.sh` and the application:

```bash
export KAFKA_PORT=19092 REGISTRY_PORT=18081 UI_PORT=18888 APP_PORT=18090
docker compose up -d
./demo.sh
KAFKA_BOOTSTRAP=localhost:$KAFKA_PORT REGISTRY_URL=http://localhost:$REGISTRY_PORT/apis/registry/v3 \
  java -jar target/quarkus-app/quarkus-run.jar
```

The UI is then at `http://localhost:18888`, and the application at `http://localhost:18090`.
