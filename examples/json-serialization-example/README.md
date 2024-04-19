# Dot net JSON with references serialization
This example demonstrate how Apicurio Registry can be used along the .net serialization libraries provided by Confluent.

It expects you to have an already available Kafka cluster and an Apicurio Registry instance up and running.

To get Apicurio Registry running you can simply do:

``
docker run -p 8080:8080 -it apicurio/apicurio-registry-mem:2.5.10.Final
``

To build it, form the project directory run `dotnet build JsonWithReferences.csproj` and then you can execute it using `dotnet run localhost:62082 localhost:8080/apis/ccompat/v7 products`
where the first argument is the location of you bootstrap servers, the second argument is the compatibility API of your Apicurio Registry instance and the last argument is the topic you want to use.
When executed, it will prompt you for product names then it will automatically consume the messages deserializing it using the artifacts registered in Apicurio Registry.