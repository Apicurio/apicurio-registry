# Apicurio Registry CloudEvents example

This is an example application that implements a REST API that consumes and produces CloudEvents.

This example application is implemented thanks to an experimental library implemented within the Apicurio Registry project. This library is used to validate incoming and outgoing CloudEvents messages in a REST API.
The validation is performed using json schemas that are previously stored in Apicurio Registry.

The idea behind this library is to provide a tool for serialization and deserialization of CloudEvents that uses Apicurio Registry to store the schemas used for serialization, deserialization or validation of CloudEvents data. This library is built on top of the CloudEvents Java SDK, that among other things provides the CloudEvent Java type.

For this PoC we only focused on **CloudEvents and http**. Meaning that, at least for now, this library primarily allows to use CloudEvents with REST services and REST clients. Also, this library only provides support for json schemas, support for other formats such as avro, protobuf,... could be easily implemented.

### Apicurio Registry CloudEvents Serde and Kafka

We decided to not focus on implementing a set of Kafka Serde classes that work with CloudEvents.

We are open to discussion if you consider it could be interesting to be able to do serdes plus validation of CloudEvents using Apicurio Registry **but** using another protocol or transport such as Kafka, AMQP, MQTT,... Feel free to create an issue or to reach out to the Apicurio team.

After implementing the serdes library for REST services we considered implementing the equivalent for Kafka but we dismissed this effort, you can find below some of our reasons.

Our current Serdes classes could be easily improved to make them more compatible with CloudEvents based use cases, and this approach would be preferrable rather than implementing a new set of Kafka Serdes classes for CloudEvents.

The [KafkaConsumer API](https://kafka.apache.org/26/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html) bounds the consumer to one class that will be the type of the message value that it will receive after deserialization, this means that all messages that a KafkaConsumer will receive are going to have the same structure.
```
KafkaConsumer<String, NewOrder> consumer;
```
However for CloudEvents use cases we don't see any value in having Kafka Serde that works with a generic type, such as the java class for CloudEvents would be.
```
KafkaConsumer<String, CloudEvent<NewOrder>> consumer;
```
If required, this approach could be easy to achieve with some improvements to our existing Serdes classes.

## Apicurio Registry CloudEvents Serde library

The Apicurio Registry CloudEvents library consists of two maven modules:
- `apicurio-registry-utils-cloud-events-serde`, provides the serialization and deserialization API, with data validation included. This component calls Apicurio Registry to fetch the required schemas to perform the serialization/deserialization/validation.
- `apicurio-registry-utils-cloud-events-provider`, this contains a jaxrs provider implemented on top of CloudEvents [java sdk for restful services](https://github.com/cloudevents/sdk-java/tree/master/http/restful-ws). This provider allows to implement REST APIs that consume and produce CloudEvents, like the CloudEvents sdk does, but validating the CloudEvents data and ensuring the data adheres to it's respective schema stored in Apicurio Registry.

This library is experimental and has not been released nor is available in the main branch of the Apicurio Registry project,
so if you are interested you can find the source code [here](https://github.com/Apicurio/apicurio-registry/tree/cloud-events/utils/cloud-events).
Also, to test the code (and to run this demo) you have to build it from source.

```
git clone -b cloud-events https://github.com/Apicurio/apicurio-registry.git

cd apicurio-registry

mvn install -am -pl 'utils/cloud-events/cloud-events-provider' -Dmaven.javadoc.skip=true

```

## Running the demo

After installing in your local maven repo the `apicurio-registry-utils-cloud-events-provider` library you have to build this app.
```
mvn package
```

Once that's done you can start Apicurio Registry, I suggest doing it with a container
```
docker run -p 8080:8080 docker.io/apicurio/apicurio-registry-mem:1.3.2.Final
```

Then create the artifacts in the registry that are used by the CloudEvents serde to validate the data that the REST API will receive
```
curl --data "@new-order-schema.json" -X POST -i -H "X-Registry-ArtifactId: io.apicurio.examples.new-order" http://localhost:8080/api/artifacts
```
```
curl --data "@processed-order-schema.json" -X POST -i -H "X-Registry-ArtifactId: io.apicurio.examples.processed-order" http://localhost:8080/api/artifacts
```

Finally it's time to run this demo app.
```
java -jar target/cloudevents-example-*-runner.jar
```

## Test the app

To test the app we are going to make a few http requests sending CloudEvents to the API.

Previously we created the artifact `io.apicurio.examples.new-order` in the registry with it's json schema.

With this request we are going to send a CloudEvent of type `new-order` and dataschema `/apicurio/io.apicurio.examples.new-order/1` to the path `/orders`. The serdes layer will read that dataschema and fetch the json schema from
Apicurio Registry in order to validate the json data adheres to the schema.
The server responds with another CloudEvent of type `io.apicurio.examples.processed-order` that has also been validated against it's stored schema in Apicurio Registry.
```
$ curl -X POST -i -H "Content-Type: application/json" -H "ce-dataschema:/apicurio/io.apicurio.examples.new-order/1" -H "ce-type:new-order" -H "ce-source:test" -H "ce-id:aaaaa" -H "ce-specversion:1.0" --data '{"itemId":"abcde","quantity":5}' http://localhost:8082/order
HTTP/1.1 200 OK
transfer-encoding: chunked
ce-source: apicurio-registry-example-api
ce-specversion: 1.0
ce-type: io.apicurio.examples.processed-order
ce-id: 005762b9-9bea-4f6e-bf78-5ac8f7c99429
ce-dataschema: apicurio-global-id-2
Content-Type: application/json

{"orderId":"c763f2b4-2356-4124-a690-b205f9baf338","itemId":"abcde","quantity":5,"processingTimestamp":"2021-01-20T16:26:40.128Z","processedBy":"orders-service","error":null,"approved":true}
```

This next curl command sends a request to another endpoint in this application. The important part of this is the implementation. This `purchase` endpoint shows the usage of the CloudEvents serde library in REST clients, allowing for producers of events to validate the CloudEvents they produce.

```
$ curl -i http://localhost:8082/purchase/abc/5
HTTP/1.1 200 OK
ce-source: apicurio-registry-example-api
transfer-encoding: chunked
ce-specversion: 1.0
ce-type: io.apicurio.examples.processed-order
ce-id: f1eabd84-ad78-4beb-9c6c-04f843abf669
Content-Length: 187
ce-dataschema: apicurio-global-id-2
Content-Type: application/json

{"orderId":"29606862-e74c-47b4-95d0-b59289ea023c","itemId":"abc","quantity":5,"processingTimestamp":"2021-01-20T16:32:06.198Z","processedBy":"orders-service","error":null,"approved":true}

```

This command shows an example of what happens when you try to send a CloudEvent using a non-existent schema.
```
$ curl -X POST -i -H "Content-Type: application/json" -H "ce-type:io.apicurio.examples.test" -H "ce-source:test" -H "ce-id:aaaaa" -H "ce-specversion:1.0" --data '{"itemId":"abcde","quantity":5}' http://localhost:8082/order
HTTP/1.1 404 Not Found
Content-Length: 0
```
