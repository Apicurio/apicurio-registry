# mix-avro

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example application showcases a scenario where Apache Avro messages are published to the same
Kafka topic using different Avro schemas. This example uses the Apicurio Registry serdes classes to serialize
and deserialize Apache Avro messages using different schemas, even if received in the same Kafka topic.
The following aspects are demonstrated:

<ol>
<li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
<li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
<li>Auto-register the Avro schema in the registry (registered by the producer)</li>
<li>Data sent as a simple GenericRecord, no java beans needed</li>
<li>Producing and consuming Avro messages using different schemas mapped to different Apicurio Registry Artifacts</li>
</ol>
<p>
Pre-requisites:

<ul>
<li>Kafka must be running on localhost:9092</li>
<li>Apicurio Registry must be running on localhost:8080</li>
</ul>

@author Fabian Martinez
@author Carles Arnal

