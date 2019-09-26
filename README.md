[![CircleCI](https://circleci.com/gh/Apicurio/apicurio-registry.svg?style=svg)](https://circleci.com/gh/Apicurio/apicurio-registry)

# Apicurio Registry

An API/Schema registry - stores and retrieves APIs and Schemas.

## Build Configuration

This project supports several build configuration options that affect the produced executables.

By default, `mvn clean install` produces an executable JAR with the *dev* Quarkus configuration profile enabled, and *in-memory* persistence implementation. 

Apicurio Registry supports 3 persistence implementations:
 - in-memory
 - Kafka
 - JPA.
 
If you enable one, a separate set of artifacts is produced with the persistence implementation available.

Additionaly, there are 2 main configuration profiles:
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
 - In the *prod* mode, you have to provide connection configuration to a PostgreSQL server as follows:
  
|Option|Command argument|Env. variable|
|---|---|---|
|Data Source URL|`-Dquarkus.datasource.url`|`QUARKUS_DATASOURCE_URL`|
|DS Username|`-Dquarkus.datasource.username`|`QUARKUS_DATASOURCE_USERNAME`|
|DS Password|`-Dquarkus.datasource.password`|`QUARKUS_DATASOURCE_PASSWORD`|

To see additional options, visit:
 - [Data Source options](https://quarkus.io/guides/datasource-guide#configuration-reference) 
 - [Hibernate options](https://quarkus.io/guides/hibernate-orm-guide#properties-to-refine-your-hibernate-orm-configuration)
    
### Kafka

*TODO*

### Docker container
The same options are available for the docker containers, but only in the form of environment variables (The command line parameters are for the `java` executable and at the moment it's not possible to pass them into the image).

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

