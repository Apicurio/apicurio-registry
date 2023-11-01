[![Verify Build Workflow](https://github.com/Apicurio/apicurio-registry/workflows/Verify%20Build%20Workflow/badge.svg)](https://github.com/Apicurio/apicurio-registry/actions?query=workflow%3A%22Verify+Build+Workflow%22)
[![Join the chat at https://apicurio.zulipchat.com/](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://apicurio.zulipchat.com/)
[![Automated Release Notes by gren](https://img.shields.io/badge/%F0%9F%A4%96-release%20notes-00B2EE.svg)](https://github-tools.github.io/github-release-notes/)

![Apicurio Registry](.assets/apicurio_registry_logo_default.svg)

An API/Schema registry - stores and retrieves APIs and Schemas.

## Build Configuration

This project supports several build configuration options that affect the produced executables.

By default, `mvn clean install` produces an executable JAR with the *dev* Quarkus configuration profile enabled, and *in-memory* persistence implementation. 

Apicurio Registry supports 4 persistence implementations:
 - In-Memory
 - KafkaSQL
 - PostgreSQL
 - SQL Server (community contributed and maintained)
  
If you enable one, a separate set of artifacts is produced with the persistence implementation available.

Additionally, there are 2 main configuration profiles:
 - *dev* - suitable for development, and
 - *prod* - for production environment.
 
### Getting started (APIs)

 ```
 ./mvnw clean install -DskipTests
 cd app/
 ../mvnw quarkus:dev
 ```
 
This should result in Quarkus and the in-memory registry starting up, with the REST APIs available on localhost port 8080:

* [API documentation](http://localhost:8080/apis)

### Getting started (UI)

```
cd ui
npm install
cd ui-app
npm run dev
```

This will start up the UI in development mode, hosted on port 8888 of your localhost:

* [User Interface](http://localhost:8888)

For more information on the UI, see the UI module's [README.md](ui/README.md).

### Build Options

- `-Pprod` enables Quarkus's *prod* configuration profile, which uses configuration options suitable for a production environment,
  e.g. a higher logging level.
- `-Psql` enables a build of `storage/sql` module and produces `apicurio-registry-storage-sql-<version>-all.zip`. This artifact uses `H2` driver in *dev* mode,
  and `PostgreSQL` driver in *prod* mode.
- `-Pmssql` enables a build of `storage/mssql` module and produces `apicurio-registry-storage-mssql-<version>-all.zip`. This artifact uses `H2` driver in *dev* mode,
  and `SQL Server` driver in *prod* mode.
- `-Pmysql` enables a build of `storage/mysql` module and produces `apicurio-registry-storage-mysql-<version>-all.zip`. This artifact uses `H2` driver in *dev* mode,
  and `MySQL` driver in *prod* mode.
- `-Pkafkasql` enables a build of the `storage/kafkasql` module and produces the `apicurio-registry-storage-kafkasql-<version>-all.zip` artifact.
- `-Pnative` *(experimental)* builds native executables. See [Building a native executable](https://quarkus.io/guides/maven-tooling#building-a-native-executable).
- `-Ddocker` *(experimental)* builds docker images. Make sure that you have the docker service enabled and running.
  If you get an error, try `sudo chmod a+rw /var/run/docker.sock`.

## Runtime Configuration

The following parameters are available for executable files:

### SQL
 - In the *dev* mode, the application expects an H2 server running at `jdbc:h2:tcp://localhost:9123/mem:registry`.
 - In the *prod* mode, you have to provide connection configuration for a PostgreSQL (or SQL Server) server as follows:
  
|Option|Command argument|Env. variable|
|---|---|---|
|Data Source URL|`-Dquarkus.datasource.jdbc.url`|`REGISTRY_DATASOURCE_URL`|
|DS Username|`-Dquarkus.datasource.username`|`REGISTRY_DATASOURCE_USERNAME`|
|DS Password|`-Dquarkus.datasource.password`|`REGISTRY_DATASOURCE_PASSWORD`|

To see additional options, visit:
 - [Data Source config](https://quarkus.io/guides/datasource) 
 - [Data Source options](https://quarkus.io/guides/datasource-guide#configuration-reference) 
 - [Hibernate options](https://quarkus.io/guides/hibernate-orm-guide#properties-to-refine-your-hibernate-orm-configuration)

### KafkaSQL
`./mvnw clean install -Pprod -Pkafkasql -DskipTests` builds the KafkaSQL artifact.
The newly built runner can be found in `/storage/kafkasql/target`
```
java -jar apicurio-registry-storage-kafkasql-<version>-SNAPSHOT-runner.jar
```
Should result in Quarkus and the registry starting up, with the ui and APIs available on localhost port 8080.
By default, this will look for a kafka instance on `localhost:9092`, see [kafka-quickstart](https://kafka.apache.org/quickstart).

Alternatively this can be connected to a secured kafka instance. For example, the following command provides the runner
with the necessary details to connect to a kafka instance using a PKCS12 certificate for TLS authentication and
scram-sha-512 credentials for user authorisation.
```
java \
-Dregistry.kafka.common.bootstrap.servers=<kafka_bootstrap_server_address> \
-Dregistry.kafka.common.ssl.truststore.location=<truststore_file_location>\
-Dregistry.kafka.common.ssl.truststore.password=<truststore_file_password> \
-Dregistry.kafka.common.ssl.truststore.type=PKCS12 \
-Dregistry.kafka.common.security.protocol=SASL_SSL \
-Dregistry.kafka.common.sasl.mechanism=SCRAM-SHA-512 \
-Dregistry.kafka.common.sasl.jaas.config='org.apache.kafka.common.security.scram.ScramLoginModule required username="<username>" password="<password>";' \
-jar storage/kafkasql/target/apicurio-registry-storage-kafkasql-2.1.6-SNAPSHOT-runner.jar
```
This will start up the registry with the persistence managed by the external kafka cluster.

## Docker containers
Every time a commit is pushed to `main` an updated set of docker images are built and pushed to Docker 
Hub.  There are several docker images to choose from, one for each storage option.  The images include:

* [apicurio-registry-mem](https://hub.docker.com/r/apicurio/apicurio-registry-mem)
* [apicurio-registry-sql](https://hub.docker.com/r/apicurio/apicurio-registry-sql)
* [apicurio-registry-mssql](https://hub.docker.com/r/apicurio/apicurio-registry-mssql)
* [apicurio-registry-kafkasql](https://hub.docker.com/r/apicurio/apicurio-registry-kafkasql)

Run one of the above docker images like this:

    docker run -it -p 8080:8080 apicurio/apicurio-registry-mem:latest-snapshot

The same configuration options are available for the docker containers, but only in the form of environment 
variables (The command line parameters are for the `java` executable and at the moment it's not possible to 
pass them into the container).  Each docker image will support the environment variable configuration options
documented above for their respective storage type.

There are a variety of docker image tags to choose from when running the registry docker images.  Each
release of the project has a specific tag associated with it.  So release `1.2.0.Final` has an equivalent
docker tag specific to that release.  We also support the following moving tags:

* `latest-snapshot` : represents the most recent docker image produced whenever the `main` branch is updated
* `latest` : represents the latest stable (released) build of Apicurio Registry
* `latest-release` : represents the latest stable (released) build of Apicurio Registry (alias for `latest` with clearer semantics)

Note that if you want to have access to the UI for Registry, you must **also** run the UI container image:

* [apicurio-registry-ui](https://hub.docker.com/r/apicurio/apicurio-registry-ui)

You might run the UI container image like this:

    docker run -it -p 8888:8080 apicurio/apicurio-registry-ui:latest-snapshot

Once both container images are running as described above, you can access the following URLs:

* [API documentation](http://localhost:8080/apis)
* [User Interface](http://localhost:8888)


## Examples

Run Apicurio Registry with Postgres:

 - Compile using `mvn clean install -DskipTests -Pprod -Psql -Ddocker`

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
    image: apicurio/apicurio-registry-sql:2.0.0-SNAPSHOT
    ports:
      - 8080:8080
    environment:
      REGISTRY_DATASOURCE_URL: 'jdbc:postgresql://postgres/apicurio-registry'
      REGISTRY_DATASOURCE_USERNAME: apicurio-registry
      REGISTRY_DATASOURCE_PASSWORD: password
```
  - Run `docker-compose -f test.yml up`

## Security

You can enable authentication for both the application REST APIs and the user interface using a server based
on OpenID Connect (OIDC). The same server realm and users are federated across the user interface and the 
REST APIs using Open ID Connect so that you only require one set of credentials.

In order no enable this integration, you will need to set the following environment variables.

### REST API Environment Variables

|Option|Env. variable|
|---|---|
|`AUTH_ENABLED`|Set to `true` to enable (default is `false`)|
|`KEYCLOAK_URL`|OIDC Server URL|
|`KEYCLOAK_REALM`|OIDC Security realm|
|`KEYCLOAK_API_CLIENT_ID`|The client for the API|

### User Interface Environment Variables

|Option|Env. variable|
|---|---|
|`REGISTRY_AUTH_TYPE`|Set to `oidc` (default is `none`)|
|`REGISTRY_AUTH_URL`|OIDC auth URL|
|`REGISTRY_AUTH_REDIRECT_URL`|OIDC redirect URL|
|`REGISTRY_AUTH_CLIENT_ID`|The client for the UI|

Note that you will need to have everything configured in your OIDC provider, before starting the application
(the realm and the two clients).

Please note that Registry supports a wide range of authentication and authorization options.
These options are too extensive to document in this README.  Consider the above to be just a starting point.
For more information see the documentation on [how to configure security in Registry](https://www.apicur.io/registry/docs/apicurio-registry/2.4.x/getting-started/assembly-configuring-the-registry.html#registry-security_registry).


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

## IntelliJ IDE

Some notes about using the IntelliJ IDE with the Apicurio Registry codebase.
IntelliJ seems to struggle with multiple sub-modules including generated code.
After importing the registry into your workspace, we recommend to ignore
the `client` sub-module.

Right click on `client/pom.xml` -> Maven -> Ignore Projects (click)

