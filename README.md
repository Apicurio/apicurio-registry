[![Verify Build Workflow](https://github.com/Apicurio/apicurio-registry/workflows/Verify%20Build%20Workflow/badge.svg)](https://github.com/Apicurio/apicurio-registry/actions?query=workflow%3A%22Verify+Build+Workflow%22)
[![Automated Release Notes by gren](https://img.shields.io/badge/%F0%9F%A4%96-release%20notes-00B2EE.svg)](https://github-tools.github.io/github-release-notes/)

# Apicurio Registry

An API/Schema registry - stores and retrieves APIs and Schemas.

## Build Configuration

This project supports several build configuration options that affect the produced executables.

By default, `mvn clean install` produces an executable JAR with the *dev* Quarkus configuration profile enabled, and *in-memory* persistence implementation. 

Apicurio Registry supports 4 persistence implementations:
 - In-Memory
 - Kafka (Topics vs. KV-Store / Streams)
 - JPA
 - Infinispan (POC / WIP)
 
If you enable one, a separate set of artifacts is produced with the persistence implementation available.

Additionally, there are 2 main configuration profiles:
 - *dev* - suitable for development, and
 - *prod* - for production environment.

### Build Options
 
 - `-Pkafka` enables a build of `storage/kafka` module and produces `apicurio-registry-storage-kafka-<version>-all.zip`.
 - `-Pjpa` enables a build of `storage/jpa` module and produces `apicurio-registry-storage-jpa-<version>-all.zip`. This artifact uses `H2` driver in *dev* mode,
   and `PostgreSQL` driver in *prod* mode.
 - `-Pprod` enables Quarkus's *prod* configuration profile, which uses configuration options suitable for a production environment, 
   e.g. a higher logging level.
 - `-Pnative` *(experimental)* builds native executables. See [Building a native executable](https://quarkus.io/guides/maven-tooling#building-a-native-executable). 
 - `-Ddocker` *(experimental)* builds docker images. Make sure that you have the docker service enabled and running.
   If you get an error, try `sudo chmod a+rw /var/run/docker.sock`.

## Runtime Configuration

The following parameters are available for executable files:

### JPA
 - In the *dev* mode, the application expects a H2 server running at `jdbc:h2:tcp://localhost:9123/mem:registry`.
 - In the *prod* mode, you have to provide connection configuration for a PostgreSQL server as follows:
  
|Option|Command argument|Env. variable|
|---|---|---|
|Data Source URL|`-Dquarkus.datasource.url`|`QUARKUS_DATASOURCE_URL`|
|DS Username|`-Dquarkus.datasource.username`|`QUARKUS_DATASOURCE_USERNAME`|
|DS Password|`-Dquarkus.datasource.password`|`QUARKUS_DATASOURCE_PASSWORD`|

To see additional options, visit:
 - [Data Source options](https://quarkus.io/guides/datasource-guide#configuration-reference) 
 - [Hibernate options](https://quarkus.io/guides/hibernate-orm-guide#properties-to-refine-your-hibernate-orm-configuration)

### Kafka

 - In the *dev* mode, the application expects a Kafka broker running at `localhost:9092`.
 - In the *prod* mode, you have to provide an environment variable `KAFKA_BOOTSTRAP_SERVERS` pointing to Kafka brokers

Kafka storage implementation uses the following Kafka API / architecture

 - Storage producer to forward REST API HTTP requests to Kafka broker
 - Storage consumer to handle previously sent  REST API HTTP requests as Kafka messages
 - Snapshot producer to send current state's snapshot to Kafka broker
 - Snapshot consumer for initial (at application start) snapshot handling

We already have sensible defaults for all these things, but they can still be overridden or added by adding appropriate properties to app's configuration. The following property name prefix must be used:

 - Storage producer: registry.kafka.storage-producer.
 - Storage consumer: registry.kafka.storage-consumer.
 - Snapshot producer: registry.kafka.snapshot-producer.
 - Snapshot consumer: registry.kafka.snapshot-consumer.

We then strip away the prefix and use the rest of the property name in instance's Properties.

e.g. registry.kafka.storage-producer.enable.idempotence=true --> enable.idempotence=true

For the actual configuration options check (although best config docs are in the code itself):
 - [Kafka configuration](https://kafka.apache.org/documentation/)

To help setup development / testing environment for the module, see kafka_setup.sh script. You just need to have KAFKA_HOME env variable set, and script does the rest.

### Streams

Streams storage implementation goes beyond plain Kafka usage and uses Kafka Streams to handle storage in a distributed and fault-tolerant way.

 - In the *dev* mode, the application expects a Kafka broker running at `localhost:9092`.
 - In the *prod* mode, you have to provide an environment variable `KAFKA_BOOTSTRAP_SERVERS` pointing to Kafka brokers and `APPLICATION_ID` to name your Kafka Streams application

Both modes require 2 topics: storage topic and globalId topic. This is configurable, by default we use storage-topic and global-id-topic names.

Streams storage implementation uses the following Kafka (Streams) API / architecture

 - Storage producer to forward REST API HTTP requests to Kafka broker
 - Streams input KStream to handle previously sent  REST API HTTP requests as Kafka messages
 - Streams KStream to handle input's KStream result
 - Both KStreams use KeyValueStores to keep current storage state

The two KeyValueStores keep the following structure:
 - storage store: <String, Str.Data> -- where the String is artifactId and Str.Data is whole artifact info: content, metadata, rules, etc
 - global id store: <Long, Str.Tuple> -- where the Long is unique globalId and Str.Tuple is a <artifactId, version> pair
 
We use global id store to map unique id to <artifactId, version> pair, which also uniquely identifies an artifact.
The data is distributed among node's stores, where we access those remote stores based on key distribution via gRPC. 

We already have sensible defaults for all these things, but they can still be overridden or added by adding appropriate properties to app's configuration. The following property name prefix must be used:

 - Storage producer: registry.streams.storage-producer.
 - Streams topology: registry.streams.topology.

We then strip away the prefix and use the rest of the property name in instance's Properties.

e.g. registry.streams.topology.replication.factor=1 --> replication.factor=1

For the actual configuration options check (although best config docs are in the code itself):
 - [Kafka configuration](https://kafka.apache.org/documentation/)
 - [Kafka Streams](https://kafka.apache.org/documentation/streams/)

To help setup development / testing environment for the module, see streams_setup.sh script. You just need to have KAFKA_HOME env variable set, and script does the rest.

## Docker containers
Every time a commit is pushed to `master` an updated set of docker images are built and pushed to Docker 
Hub.  There are several docker images to choose from, one for each storage option.  The images include:

* [apicurio-registry-mem](https://hub.docker.com/r/apicurio/apicurio-registry-mem)
* [apicurio-registry-jpa](https://hub.docker.com/r/apicurio/apicurio-registry-jps)
* [apicurio-registry-infinispan](https://hub.docker.com/r/apicurio/apicurio-registry-infinispan)
* [apicurio-registry-streams](https://hub.docker.com/r/apicurio/apicurio-registry-streams)
* [apicurio-registry-kafka](https://hub.docker.com/r/apicurio/apicurio-registry-kafka)

Run one of the above docker images like this:

    docker run -it -p 8080:8080 apicurio/apicurio-registry-mem

The same configuration options are available for the docker containers, but only in the form of environment 
variables (The command line parameters are for the `java` executable and at the moment it's not possible to 
pass them into the container).  Each docker image will support the environment variable configuration options
documented above for their respective storage type.

There are a variety of docker image tags to choose from when running the registry docker images.  Each
release of the project has a specific tag associated with it.  So release `1.2.0.Final` has an equivalent
docker tag specific to that release.  We also support the following moving tags:

* `latest-snapshot` : represents the most recent docker image produced whenever the `master` branch is updated
* `latest-release` : represents the latest stable (released) build of Apicurio Registry
* `latest` : represents the absolute newest build - essentially the newer of `latest-release` or `latest-snapshot`

## Examples

Run Apicurio Registry with Postgres:

 - Compile using `mvn clean install -DskipTests -Pprod -Pjpa -Ddocker`

 - Then create a docker-compose file `test.yml`: 
```yaml
version: '3.1'

services:
  postgres:
    image: postgres
    environment:
      POSTGRES_USER: apicurio-registry
      POSTGRES_PASSWORD: password
  app:
    image: apicurio/apicurio-registry-jpa:1.0.0-SNAPSHOT
    ports:
      - 8080:8080
    environment:
      QUARKUS_DATASOURCE_URL: 'jdbc:postgresql://postgres/apicurio-registry'
      QUARKUS_DATASOURCE_USERNAME: apicurio-registry
      QUARKUS_DATASOURCE_PASSWORD: password
```
  - Run `docker-compose -f test.yml up`

## Security

To run Apicurio Registry against secured Kafka broker(s) in Docker/Kubernetes/OpenShift,
you can put the following system properties into JAVA_OPTIONS env var:

* -D%dev.registry.streams.topology.security.protocol=SSL
* -D%dev.registry.streams.topology.ssl.truststore.location=[location]
* -D%dev.registry.streams.topology.ssl.truststore.password=[password]
* -D%dev.registry.streams.topology.ssl.truststore.type=[type]
* (optional) -D%dev.registry.streams.topology.ssl.endpoint.identification.algorithm=
* -D%dev.registry.streams.storage-producer.security.protocol=SSL
* -D%dev.registry.streams.storage-producer.ssl.truststore.location=[location]
* -D%dev.registry.streams.storage-producer.ssl.truststore.password=[password]
* -D%dev.registry.streams.storage-producer.ssl.truststore.type=[type]
* (optional) -D%dev.registry.streams.storage-producer.ssl.endpoint.identification.algorithm=
* etc ...

Of course that %dev depends on the Quarkus profile you're gonna use -- should be %prod when used in production.


## Eclipse IDE

Some notes about using the Eclipse IDE with the Apicurio Registry codebase.  Before
importing the registry into your workspace, we recommend some configuration of the 
Eclipse IDE.

### Lombok Integration

We use the Lombok code generation utility in a few places.  This will cause problems
when Eclipse builds the sources unless you install the Lombok+Eclipse integration.  To
do this, either download the Lombok JAR or find it in your `.m2/repository`
directory (it will be available in `.m2` if you've done a maven build of the registry).
Once you find that JAR, simply "run" it (e.g. double-click it) and using the resulting
UI installer to install Lombok support in Eclipse.

### Maven Dependency Plugin (unpack, unpack-dependencies)

We use the **maven-dependency-plugin** in a few places to unpack a maven module in the
reactor into another module.  For example, the `app` module unpacks the contents of
the `ui` module to include/embed the user interface into the running application.
Eclipse does not like this.  To fix this, configure the Eclipse Maven "Lifecycle Mappings"
to ignore the usage of **maven-dependency-plugin**.  

* Open up **Window->Preferences**
* Choose **Maven->Lifecycle Mappings**
* Click the button labeled **Open workspace lifecycle mappings metadata**
* This will open an XML file behind the preferences dialog.  Click **Cancel** to close the Preferences.
* Add the following section to the file:

```
    <pluginExecution>
      <pluginExecutionFilter>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <versionRange>3.1.2</versionRange>
        <goals>
          <goal>unpack</goal>
          <goal>unpack-dependencies</goal>
        </goals>
      </pluginExecutionFilter>
      <action>
        <ignore />
      </action>
    </pluginExecution>
```

* Now go back into **Maven->Lifecycle Mappings** -> **Maven->Lifecycle Mappings** and click 
the **Reload workspace lifecycle mappings metadata** button.
* If you've already imported the Apicurio projects, select all of them and choose **Maven->Update Project**.

### Prevent Eclipse from aggressively cleaning generated classes

We use some Google Protobuf files and a maven plugin to generate some Java classes that
get stored in various modules' `target` directories.  These are then recognized by m2e
but are sometimes deleted during the Eclipse "clean" phase.  To prevent Eclipse from
over-cleaning these files, find the **os-maven-plugin-1.6.2.jar** JAR in your 
`.m2/repository` directory and copy it into `$ECLIPSE_HOME/dropins`.
