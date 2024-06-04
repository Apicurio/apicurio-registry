# simple-avro-maven

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
scenario with Avro as the serialization type and the Schema pre-registered via a Maven plugin.
The following aspects are demonstrated:

<ol>
<li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
<li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
<li>Pre-register the Avro schema in the registry via the Maven plugin</li>
<li>Data sent as a simple GenericRecord, no java beans needed</li>
</ol>

Pre-requisites:

<ul>
<li>Kafka must be running on localhost:9092</li>
<li>Apicurio Registry must be running on localhost:8080</li>
<li>Schema is registered by executing "mvn io.apicurio:apicurio-registry-maven-plugin:register@register-artifact"</li>
</ul>

Note that this application will fail if the above maven command is not run first, since
the schema will not be present in the registry.

@author eric.wittmann@gmail.com

