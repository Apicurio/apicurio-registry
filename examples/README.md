The examples on this repository have been moved to the [main Apicurio Registry Repository](https://github.com/Apicurio/apicurio-registry/examples).



# Apicurio Registry Example Applications
This repository contains a set of example applications (mostly Kafka applications) that use the
Apicurio Registry as part of their workflow.  The registry is typically used to store schemas 
used by Kafka serializer and deserializer classes.  These serdes classes will fetch the schema
from the registry for use during producing or consuming operations (to serializer, deserializer, 
or validate the Kafka message payload).

Each example in this repository attempts to demonstrate some specific use-case or configuration.
There are numerous options available when integrating with the registry, and therefore the set
of examples found here may not cover every configuration permutation.

# List of Examples
A list of examples is included below, with descriptions and explanations of each covered use-case.

## Simple Avro Example
This example application demonstrates the basics of using the registry in a very simple Kafka 
publish/subscribe application using Apache Avro as the schema technology used to serialize 
and deserialize message payloads.  

## Simple JSON Schema Example
This example application demonstrates the basics of using the registry in a very simple Kafka 
publish/subscribe application using JSON Schema to validate message payloads when both producing 
and consuming them.  JSON Schema is not a serialization technology, but rather is only used for
validation.  Therefore it can be enabled or disabled in the serializer and deserializer.

## Confluent Serdes Integration
This example shows how Apicurio Registry serdes classes can be used along with Confluent serdes
classes in a mixed application environment.  In other words, some applications can be using
Confluent classes while other applications can be using Apicurio Registry classes - and they
can all work together seamlessly with just a little bit of extra configuration.  This example
is essentially the same as the Simple Avro Example, but using a Confluent serializer with an
Apicurio Registry deserializer.

## Avro Bean Example
This example demonstrates how to use Avro as the schema and serialization technology while 
using a Java Bean as the Kafka message payload.  This is essentially the same as the Simple
Avro Example, but using a java bean instead of a `GenericRecord` as the message payload.

## Custom ID Strategy Example
This example demonstrates how to use a custom Global ID strategy.  The Global ID strategy is
used by a producer (serializer) application to lookup (or create) the Schema it is using for
serialization.  Apicurio Registry comes with some useful implementations of the Global ID
strategy out of the box, but it is possible to create your own.  This example is essentially
the same as the Simple Avro Example, except instead of using one of the default Apicurio
Registry Global ID strategies, it uses a custom one.

## Simple Avro Maven Example
This example application demonstrates how to use the Apicurio Registry maven plugin to 
pre-register an Avro schema so that it does not need to be embedded within the producer
application.  Note that this example will fail unless the maven plugin is executed before
the Java application.  See the javadoc in the example for details.

## Rest Client example
This example application demonstrates how to use the Apicurio Registry rest client to create, 
delete, or fetch schemas. This example contains two basic java application, one showing how to 
improve the logs by logging all the request and response headers and a basic example on how to 
use the client.

## Mix Avro Schemas Example
This example application showcases an scenario where Apache Avro messages are published to the same
Kafka topic using different Avro schemas. This example uses the Apicurio Registry serdes classes to serialize
and deserialize Apache Avro messages using different schemas, even if received in the same Kafka topic.

## Cloud Events PoC
This is an example application that implements a REST API that consumes and produces CloudEvents.
This example application showcases an experimental library from apicurio-registry project. This library is used to validate incoming and outgoing CloudEvents messages in the REST API.
The validation is performed against json schemas that are stored in Apicurio Registry. For a more detailed explanation go [here](./cloudevents/README.md).
