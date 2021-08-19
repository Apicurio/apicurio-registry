# Apicurio Confluent Schema Registry Export Utility

This is a command line utility application to help on the migrate process from Confluent Schema Registry to Apicurio Registry.

This command line application connects to the API of Confluent Schema Registry and exports a zip file compatible with the import admin API available in Apicurio Registry.

To use this tool you first need to build it. We will build a jar file. Execute:
```
mvn package -Pprod
```

Then you can execute it like this:
```
java -jar target/apicurio-registry-utils-exportConfluent-2.1.0-SNAPSHOT-runner.jar http://localhost:8081/
```
It will create a `confluent-schema-registry-export.zip` in the current directory.

You can configure the client used to connect to the registry API like this:
```
java -jar target/apicurio-registry-utils-exportConfluent-2.1.0-SNAPSHOT-runner.jar http://localhost:8081/ --client-props bearer.auth.credentials.source=BEARER_TOKEN
```