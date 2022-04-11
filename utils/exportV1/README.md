# Apicurio Registry Export Utility for 1.X versions

This is a command line utility application to help on the upgrade process from Apicurio Registry 1.X to Apicurio Registry 2.0.

Apicurio Registry 2.0 now provides an import/export admin API that allows you to easily migrate your data between Apicurio Registry deployments.

This command line application connects to the API of Apicurio Registry 1.X and exports a zip file compatible with the import admin API available in Apicurio Registry 2.0. Allowing for easy migration and upgrade process between the two major Apicurio Registry versions.

To use this tool you first need to build it. We will build a jar file. Execute:
```
mvn package -Pprod
```

Then you can execute it like this:
```
java -jar target/apicurio-registry-utils-exportV1-2.2.3-SNAPSHOT-runner.jar http://localhost:8080/api
```
It will create a `registry-export.zip` in the current directory.

### Feature flags

This tool provides flags for specific features:

***Note: if used, flags have to be provided in the order they are documented here***

+ `--match-content-id` This flag will make the globalId and contentId of all artifacts to match. Useful for confluent compatibility.
i.e:
```
java -jar target/apicurio-registry-utils-exportV1-2.2.3-SNAPSHOT-runner.jar http://localhost:8080/api --match-content-id
```

+ `--client-props <config-key>=<config-value>` This flag allows to pass config values to the underlying rest client.
i.e: You can configure the client used to connect to the registry API like this:
```
java -jar target/apicurio-registry-utils-exportV1-2.2.3-SNAPSHOT-runner.jar http://localhost:8080/api --client-props apicurio.registry.request.headers.x-custom-header=testvalue
```

+ `--insecure` This flag will make the client to trust all SSL certificates.