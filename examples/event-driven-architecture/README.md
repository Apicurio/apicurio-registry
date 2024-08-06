# Event-driven architecture with Apicurio Registry

## Kafka, ksqldb, Kafka-ui, apicurio-registry and Debezium together

This tutorial demonstrates how to use [Debezium](https://debezium.io/)
to monitor the PostgreSQL database used by Apicurio Registry.
As the data in the database changes, such as by adding new schemas,
you will see the resulting event streams.

## Avro serialization

The [Apicurio Registry](https://github.com/Apicurio/apicurio-registry)
open-source project provides several components that work with Avro:

- An Avro converter that you can specify in Debezium connector configurations
  This converter maps Kafka Connect schemas to Avro schemas.
  The converter then uses the Avro schemas to serialize the record keys and values
  into Avro’s compact binary form.

- An API and schema registry that tracks:

  - Avro schemas that are used in Kafka topics
  - Where the Avro converter sends the generated Avro schemas

### Prerequisites

- Docker is installed and running.

  This tutorial uses Docker and the Linux container images to run the required services. You should use the
  latest version of Docker. For more information, see
  the [Docker Engine installation documentation](https://docs.docker.com/engine/installation/).

## Starting the services

1. Clone this repository:

    ```console
    git clone https://github.com/Apicurio/apicurio-registry.git
    ```

1. Change to the following directory:

    ```console
    cd examples/event-driven-architecture
    ```

1. Start the environment

    ```consolee
    docker compose up -d
    ```

The last command will start the following components:

- Single node Zookeeper and Kafka cluster
- Single node Kafka Connect cluster
- Apicurio service registry
- PostgreSQL (ready for CDC)
- KsqlDb instance
- Kafka UI

## Apicurio converters

Configuring Avro at the Debezium Connector involves specifying the converter
and schema registry as a part of the connector's configuration.
The configuration file sets the (de-)serializers to use Avro
and specifies the location of the Apicurio registry.

> The container image used in this environment includes all the required libraries to access the connectors and converters.

The following lines are required to set the **key** and **value** converters
and their respective registry configurations:

```json
{
  "value.converter.apicurio.registry.url": "http://schema-registry:8080/apis/registry/v2",
  "key.converter.apicurio.registry.url": "http://schema-registry:8080/apis/registry/v2",
  "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
  "key.converter.apicurio.registry.auto-register": "true",
  "key.converter": "io.apicurio.registry.utils.converter.AvroConverter",
  "value.converter.apicurio.registry.as-confluent": "true",
  "value.converter.apicurio.registry.use-id": "contentId"
}
```

> The compatibility mode allows you to use other providers tooling to deserialize and reuse the schemas in the Apicurio service registry.

### Create the connector

Let's create the Debezium connector to start capturing changes in the database.

1. Create the connector using the REST API.
You can execute this ste either by using the `curl` command below
or by creating the connector from the Kafka UI.

    ```console
    curl http://localhost:8083/connectors -H 'Content-Type: application/json' -d @studio-connector.json
    ```

### Check the data

The previous step created and started the connector.
Now, all the data inserted in the Apicurio Registry database
will be captured by Debezium and sent as events into Kafka.

## Summary

This example allows you to test how to start a full event-driven architecture.
How you use the produced events is up to you,
such as creating streams or tables in ksqlDB, etc.
