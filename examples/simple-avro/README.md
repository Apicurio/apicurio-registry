# simple-avro

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
scenario with Avro as the serialization type.  The following aspects are demonstrated:

<ol>
<li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
<li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
<li>Auto-register the Avro schema in the registry (registered by the producer)</li>
<li>Data sent as a simple GenericRecord, no java beans needed</li>
</ol>
<p>
Pre-requisites:

<ul>
<li>Kafka must be running on localhost:9092</li>
<li>Apicurio Registry must be running on localhost:8080</li>
</ul>

@author eric.wittmann@gmail.com

