# custom-strategy

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
scenario with Avro as the serialization type.  The following aspects are demonstrated:

<ol>
<li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
<li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
<li>Register the Avro schema in the registry using a custom Global Id Strategy</li>
<li>Data sent as a simple GenericRecord, no java beans needed</li>
</ol>

Pre-requisites:

<ul>
<li>Kafka must be running on localhost:9092 or the value must be changed accordingly.</li>
<li>Apicurio Registry must be running on localhost:8080 or the value must be changed accordingly.</li>
</ul>

@author eric.wittmann@gmail.com
@author carles.arnal@redhat.com

