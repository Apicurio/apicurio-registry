# Apicurio Confluent Schema Registry Export Utility

This is a command line utility application to help on the migrate process from Confluent Schema Registry to Apicurio Registry.

This command line application connects to the API of Confluent Schema Registry and exports a zip file compatible with the import admin API available in Apicurio Registry.

## Get Started

To use this tool you first need to build it. We will build a jar file. Execute:
```
mvn package -Pprod
```

Then you can execute it like this:
```
java -jar target/apicurio-registry-utils-exportConfluent-3.0.0-SNAPSHOT-runner.jar http://localhost:8081/
```
It will create a `confluent-schema-registry-export.zip` in the current directory.

## Configuration

You can configure the client used to connect to the registry API like this:
```
java -jar target/apicurio-registry-utils-exportConfluent-3.0.0-SNAPSHOT-runner.jar http://localhost:8081/ --client-props bearer.auth.credentials.source=BEARER_TOKEN
```

To allow insecure https certificates, you can use `--insecure` parameter.

## Run with Docker

You can also run this tool using Docker. First, build the image:
```bash
docker build -t apicurio/registry-utils-export-confluent -f utils/exportConfluent/src/main/docker/Dockerfile.jvm .
```

Then run it:
```bash
docker run --rm -v $(pwd):/out apicurio/registry-utils-export-confluent http://host.docker.internal:8081/ --output /out/confluent-export.zip
```
Note: Use `host.docker.internal` to connect to a registry running on your host machine.

## Import data into Registry

You can import your data into Apicurio Registry using curl:
```
curl -X POST "http://<registry-url>/apis/registry/v3/admin/import" \
  -H "Accept: application/json" -H "Content-Type: application/zip" \
  --data-binary @confluent-schema-registry-export.zip
```

If you already have some data in your Apicurio Registry, you can use `X-Registry-Preserve-ContentId` header to avoid id conflicts.

**Warning: Your data will be imported with different content ids.**
```
curl -X POST "http://<registry-url>/apis/registry/v3/admin/import" \
  -H "Accept: application/json" -H "Content-Type: application/zip" \
  -H "X-Registry-Preserve-ContentId: false" \
  --data-binary @confluent-schema-registry-export.zip
```
