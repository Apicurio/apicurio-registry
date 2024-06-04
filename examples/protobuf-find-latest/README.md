# protobuf-find-latest

This is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/

## Instructions


This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
scenario with Protobuf as the serialization type.  The following aspects are demonstrated:

<ol>
<li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
<li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
<li>Manually registering the Protobuf schema in the registry (registered using the RegistryClient before running the producer/consumer), this would be equivalent to using the maven plugin or a custom CI/CD process</li>
<li>Data sent as a custom java bean and received as the same java bean</li>
</ol>

Pre-requisites:

<ul>
<li>Kafka must be running on localhost:9092</li>
<li>Apicurio Registry must be running on localhost:8080</li>
</ul>

@author eric.wittmann@gmail.com
@author carles.arnal@redhat.com

