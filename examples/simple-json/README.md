# simple-json

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
scenario with JSON as the serialization type (and JSON Schema for validation).  Because JSON
Schema is only used for validation (not actual serialization), it can be enabled and disabled
without affecting the functionality of the serializers and deserializers.  However, if
validation is disabled, then incorrect data could be consumed incorrectly.

The following aspects are demonstrated:

<ol>
<li>Register the JSON Schema in the registry</li>
<li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
<li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
<li>Data sent as a MessageBean</li>
</ol>

Pre-requisites:

<ul>
<li>Kafka must be running on localhost:9092</li>
<li>Apicurio Registry must be running on localhost:8080</li>
</ul>

@author eric.wittmann@gmail.com

