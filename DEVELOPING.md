# Developing Apicurio Registry

This guide covers building Apicurio Registry from source, the available build options,
running the tests, and configuring your IDE.

For the contribution process, legal requirements, PR guidelines, and versioning policy,
see [CONTRIBUTING.md](CONTRIBUTING.md). For a quick start and end-user documentation, see
the [README](README.md) and the
[Apicurio Registry documentation site](https://www.apicur.io/registry/docs/).

## Build Configuration

**Build requirement:** JDK 21 or newer is required to build the project (the build tooling, e.g. Checkstyle, needs a Java 21+ runtime). The produced artifacts still target Java 17.

This project supports several build configuration options that affect the produced executables.

By default, `mvn clean install` produces an executable JAR with the *dev* Quarkus configuration profile enabled, and *in-memory* persistence implementation.

Apicurio Registry supports 4 persistence implementations:
 - In-Memory
 - KafkaSQL
 - PostgreSQL
 - SQL Server (community contributed and maintained)

Starting with Apicurio Registry 3.0, we now produce a single artifact suitable for running any storage variant.

Which storage variant will be used is determined by the following configuration:

| Option                   | Command argument          | Env. variable           |
|--------------------------|---------------------------|-------------------------|
| Registry Storage Variant | `-Dapicurio.storage.kind` | `APICURIO_STORAGE_KIND` |

For this property, there are four possible values:
- *sql* - for the SQL storage variant.
- *kafkasql* - for the KafkaSQL storage variant.
- *gitops* - for the GitOps storage variant (experimental; requires `APICURIO_FEATURES_EXPERIMENTAL_ENABLED=true`).
- *kubernetesops* - for the Kubernetes ConfigMap storage variant (experimental; requires `APICURIO_FEATURES_EXPERIMENTAL_ENABLED=true`).

Additionally, there are 2 main configuration profiles:
 - *dev* - suitable for development, and
 - *prod* - for production environment.

Runtime configuration options for the produced executables (data source, security, and more)
are documented on the [Apicurio Registry documentation site](https://www.apicur.io/registry/docs/).

## Build Tiers

The project uses a three-tier build system to allow developers to build only what they need:

| Tier        | Flag      | What's included                                         | Use case                           |
|-------------|-----------|--------------------------------------------------------|------------------------------------|
| **Local**   | `-Dlocal` | Core server, Java SDK, schema utilities, serializers — skips javadoc, source JARs, checkstyle, assembly | Quick local development iteration |
| **Default** | *(none)*  | Local + CLI, docs, distribution                         | Normal development                 |
| **Full**    | `-Dfull`  | Default + MCP server, Go SDK, operator, extra utilities | CI builds, releases                |

```bash
# Local: core server + serializers, skip non-essential plugins
./mvnw clean install -Dlocal -DskipTests

# Default: normal development
./mvnw clean install -DskipTests

# Full: everything
./mvnw clean install -Dfull -DskipTests
```

Dev mode:

```bash
cd app && ../mvnw quarkus:dev -Dlocal
```

Integration tests and examples are always opt-in via their own profiles:
`-Pintegration-tests`, `-Pexamples`.

## Build Properties

| Property              | Purpose                                                                                  |
|-----------------------|------------------------------------------------------------------------------------------|
| `-Pprod`              | Enables Quarkus *prod* configuration profile (higher logging level, production defaults) |
| `-DskipTests`         | Skip running tests (test sources are still compiled)                                     |
| `-Dmaven.test.skip=true` | Skip compiling and running tests                                                      |
| `-DcliSkipNative`     | Skip CLI native image compilation (no executable is produced, but tests can still run)   |
| `-DskipOperatorTests` | Skip operator tests (default: `true`, requires a running cluster)                        |

## Dependency Analysis

`dependency:analyze` cannot be run directly over the full reactor: the `app` module
declares a test dependency of type `maven-plugin` on `apicurio-registry-maven-plugin`,
and Maven cannot satisfy `maven-plugin`-typed dependencies from the reactor when
`dependency:analyze` forks `test-compile`. Both commands below carry `-Dfull` to
ensure full reactor coverage (all modules including CLI, operator, Go SDK, etc.). The
`dependency-check` Maven profile works around this by using
[`dependency:analyze-only`](https://maven.apache.org/plugins/maven-dependency-plugin/analyze-only-mojo.html)
instead, which does not fork the build, binding it to the `package` phase instead of
running it as a standalone goal. `analyze-only` requires the classes it inspects to
already be compiled. Binding it to `package` guarantees that, because Maven runs the
normal (non-forked) `compile`, `test-compile`, and `package` phases for every module,
in reactor order, before `analyze-only` runs for that module. This is what actually
gives full reactor coverage. Binding it to an earlier phase such as `validate` was
tried first and failed partway through the reactor (`BUILD FAILURE` at module 46/67),
because sibling modules outside `app`'s own dependency closure hadn't been
compiled/packaged yet and Maven couldn't resolve them as dependencies. The check is
still a two-step procedure:

```bash
# Step 1: build app and its dependency closure so apicurio-registry-maven-plugin
# (and everything else app needs) is installed to the local repository.
./mvnw -T1 install -pl app -am -DskipTests -Dfull -DcliSkipNative -q

# Step 2: run the report-only dependency analysis over the full reactor.
./mvnw -T1 -Pdependency-check package -DskipTests -Dfull -DcliSkipNative
```

The profile configures `analyze-only` with `ignoreNonCompile=true` (ignore
runtime/provided/test/system scopes when flagging unused dependencies) and
`failOnWarning=false` (report only, never breaks the build).

**`-T1` is required**, not optional: `.mvn/maven.config` sets `-T 1C` globally, and a
parallel build interleaves per-module log output, which silently misattributes
warnings to the wrong module. Always pass `-T1` explicitly for both steps when reading
the analyze output.

**Results on the `app` module are false-positive-heavy.** `app` is a Quarkus
application, and Quarkus extensions inject capabilities (e.g. CDI beans, config) at
build time in ways a bytecode-based analyzer cannot see, so `analyze-only` routinely
flags used Quarkus extensions (e.g. `io.quarkus:quarkus-core`, `io.quarkus:quarkus-arc`)
as "used undeclared" or unused. Treat `app` warnings with skepticism and cross-check
before acting on them. See [issue #9136](https://github.com/Apicurio/apicurio-registry/issues/9136)
for background, and [quarkus-extension-analyzer](https://github.com/paoloantinori/quarkus-extension-analyzer)
for a Quarkus-aware alternative that is being explored separately.

## Testing

Unit tests run as part of the normal build (`mvn clean install`). Integration tests
are in a separate module and need to be explicitly enabled:

```bash
# Run default integration tests (smoke + serdes + acceptance)
./mvnw verify -Pintegration-tests -pl integration-tests -am
```

See the [integration tests module](integration-tests/) for test groups, deployment
modes, and detailed usage instructions.

## Running with Postgres (docker-compose)

Run Apicurio Registry with Postgres:

 - Compile using `mvn clean install -DskipTests -Pprod -Ddocker`

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
    image: apicurio/apicurio-registry:latest-release
    ports:
      - 8080:8080
    environment:
      APICURIO_STORAGE_KIND: 'sql'
      APICURIO_STORAGE_SQL_KIND: 'postgresql'
      APICURIO_DATASOURCE_URL: 'jdbc:postgresql://postgres/apicurio-registry'
      APICURIO_DATASOURCE_USERNAME: apicurio-registry
      APICURIO_DATASOURCE_PASSWORD: password
```
  - Run `docker-compose -f test.yml up`

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
