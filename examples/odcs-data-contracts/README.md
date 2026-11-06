# ODCS data contracts

A complete, local example of Apicurio Registry's data contracts:

- **An ODCS contract** ([`contracts/orders-contract.yaml`](contracts/orders-contract.yaml)), in the
  [Open Data Contract Standard](https://bitol-io.github.io/open-data-contract-standard/) v3.1
  format, says who owns the `OrderEvent` schema, which fields are sensitive, and what a valid
  order looks like.
- **The registry** stores the contract and projects it onto the schema as labels, field tags and
  CEL rules, then runs those rules on demand, with a lifecycle, search and audit on top.
- **A Kafka producer** ([`ContractSerdesDemo`](src/main/java/io/apicurio/registry/examples/ContractSerdesDemo.java))
  enforces the contract's rules in the Apicurio serializer, so an invalid order never reaches Kafka.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It walks through every feature in the
API and in the UI, with commands, expected output and screenshots, in about 30 minutes, and works
as a script for a team demo.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
docker compose up -d     # starts the Registry, its UI and Kafka
./demo.sh                # registers the schema, submits the contract, then checks the result
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a check
fails). Then open the UI at <http://localhost:8888>, and go to *Explore*, `orders`, `OrderEvent`,
*Contract*.

To explore the API yourself, load the helper commands:

```bash
source ./demo-env.sh
check '{"orderId":"A1","totalAmount":-5}'       # runs the contract's rules: "passed": false
call GET $ORDER/contract/metadata                # the contract metadata of orders/OrderEvent
```

To see the rules enforced in a Kafka producer (needs Java 17+; see [Notes](#notes)):

```bash
../../mvnw -q compile exec:java
```

Stop and remove everything with `docker compose down -v`.

## Services

| Service               | URL                   | Purpose                                     |
|-----------------------|-----------------------|---------------------------------------------|
| Apicurio Registry API | http://localhost:8081 | Registry, in-memory storage, no authentication |
| Apicurio Registry UI  | http://localhost:8888 | Web console                                 |
| Kafka                 | localhost:9092        | Single-node broker for the producer step    |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough (API and UI) and presenter notes |
| [`images/`](images) | The guide's UI screenshots |
| [`docker-compose.yml`](docker-compose.yml) | The three services |
| [`contracts/orders-contract.yaml`](contracts/orders-contract.yaml) | The ODCS contract |
| [`schemas/`](schemas) | Versions 1 and 2 of the `OrderEvent` Avro schema |
| [`demo.sh`](demo.sh) | Creates the sample data and checks the projection and rule execution |
| [`demo-env.sh`](demo-env.sh) | Shell helpers (`call`, `status`, `submit`, `check`) for the API |
| [`src/main/java/.../ContractSerdesDemo.java`](src/main/java/io/apicurio/registry/examples/ContractSerdesDemo.java) | Kafka producer and consumer with contract rules enabled |

## Notes

- **Other ports:** if 8081, 8888 or 9092 are in use, see
  [Using other ports](GUIDE.md#using-other-ports) in the guide.
- **The Kafka step needs the snapshot serializer:** the example depends on the
  `3.4.0-SNAPSHOT` Apicurio serializer, which is not published to Maven Central. Install it
  once from the repository root with
  `./mvnw install -pl serdes/kafka/avro-serde -am -DskipTests -Dlocal`.
- **Images:** the example uses the `latest-snapshot` images. To run a locally built registry,
  build it (`./mvnw install -pl distro/docker -am -DskipTests`), then build
  `distro/docker/target/docker/Dockerfile.jvm` as `quay.io/apicurio/apicurio-registry:latest-snapshot`.
- **Local only:** no authentication and in-memory storage. Do not use this configuration in
  production.

## Learn more

- *Apicurio Registry data contracts* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-data-contracts.adoc)):
  the contract format, the REST API, and the serializer settings.
- [ADR-0002](../../adr/0002-odcs-native-data-contracts.md): how it is designed.
