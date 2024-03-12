# Kafka, ksqldb, Kafka-ui, apicurio-registry and Debezium together

This tutorial demonstrates how to use [Debezium](https://debezium.io/) to monitor the PostgreSQL database used by Apicurio Registry. As the
data in the database changes, by adding e.g. new schemas, you will see the resulting event streams.

## Avro serialization

The [Apicurio Registry](https://github.com/Apicurio/apicurio-registry) open-source project provides several
components that work with Avro:

- An Avro converter that you can specify in Debezium connector configurations. This converter maps Kafka
  Connect schemas to Avro schemas. The converter then uses the Avro schemas to serialize the record keys and
  values into Avro’s compact binary form.

- An API and schema registry that tracks:

    - Avro schemas that are used in Kafka topics
    - Where the Avro converter sends the generated Avro schemas

### Prerequisites

- Docker and is installed and running.

  This tutorial uses Docker and the Linux container images to run the required services. You should use the
  latest version of Docker. For more information, see
  the [Docker Engine installation documentation](https://docs.docker.com/engine/installation/).

## Starting the services

1. Clone this repository:

    ```bash
    git clone https://github.com/Apicurio/apicurio-registry-examples.git
    ```

1. Change to the following directory:

    ```bash
    cd event-driven-architecture
    ```

1. Start the environment

    ```bash
    docker-compose up -d
    ```

The last command will start the following components:

- Single node Zookeeper and Kafka cluster
- Single node Kafka Connect cluster
- Apicurio service registry
- PostgreSQL (ready for CDC)
- KsqlDb instance
- Kafka UI

## Apicurio converters

Configuring Avro at the Debezium Connector involves specifying the converter and schema registry as a part of
the connectors configuration. The connector configuration file configures the connector but explicitly sets
the (de-)serializers for the connector to use Avro and specifies the location of the Apicurio registry. 

> The container image used  in this environment includes all the required libraries to access the connectors and converters.

The following are the lines required to set the **key** and **value** converters and their respective registry
configuration:

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

Let's create the Debezium connector to start capturing the changes of the database.

1. Create the connector using the REST API. You can execute this step either by using the curl command below
   or by creating the connector from the Kafka UI.

    ```bash
    curl -X POST http://localhost:8083/connectors -H 'content-type:application/json' -d @studio-connector.json
    ```

### Check the data

The previous step created and started the connector. Now, all the data inserted in the Apicurio Registry database will be captured by Debezium
and sent as events into Kafka.

## Summary

By using this example you can test how to start a full even driven architecture, but it's up to you how to use the produced events in e.g. ksqldb to create streams/tables etc.
