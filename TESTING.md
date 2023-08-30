# Running Apicurio Registry testsuite

This document describes the Apicurio Registry testsuite and how to run it.

Apicurio Registry testsuite has various types of tests: unit tests and integration tests(these can be executed locally or against kubernetes/openshift).

## Unit Tests

Quick tests that verify specific functionalities or components of the application. Each maven module can have it's own set of unit tests.
For the Apicurio Registry app they can be found in `app/src/test`

Because Apicurio Registry is a Quarkus application we use `@QuarkusTest` for the unit tests, that allow us to run multiple different configurations of 
the application, easily provide mocks or external dependencies... QuarkusTest allows us to easily verify feature flags or config properties that change completely the behavior of the application. In order to do that we use `@QuarkusTestProfile` quite often.

Unit tests are executed as part of the project build. You can build the project and run the tests by executing this command:
```
make build-all
```

## Integration Tests

Located under `integration-tests`. We have a set of tests for the current version of Apicurio Registry that are run in [CI](.github/workflows/integration-tests.yaml).

This set of tests are mainly designed to work in two different modes:

+ Apicurio Registry and required infrastructure deployed locally (processes, docker containers, mocks, ...) by the testsuite. This uses the @QuarkusIntegrationTest annotation to run the required infrastructure. This is useful for running the tests in the IDE for debugging purposes.
+ Apicurio Registry and required infrasturcture are deployed externally and connection details have to be provided in order to execute the tests.

### ITs with local infrastructure

This is the normal mode used when you execute the testsuite. Because Apicurio Registry supports various storage backends and various deployment time configurations(such as multitenancy, authentication,...) this tests deploy different components depending on the test executed. This is achieved using Quarkus profiles. For example, when a multitenancy test is executed, a tenant-manager instance will be deployed.

When running from the terminal, the configuration is provided via maven profiles. You can find all the available maven profiles [here](integration-tests/pom.xml)

When executing the testsuite you normally provide two profiles:
+ test profile (which determines the tests that will be executed), with the following options: all, ci, smoke, serdes, ui, acceptance, auth, multitenancy, migration, sqlit, kafkasqlit.
+ storage variant to test (which determines the storage backend that will be deployed, and therefore tested), the available options for running the test locally are: local-mem , local-sql, local-mssql , local-kafka.

As you might expect, this testsuite mode depends on the rest of the project to be built first, in order to have the application jars/images available or the serdes module to be available as well.

For running the smoke tests group using the sql variant, first run `mvn clean install -Psql` (this command will execute the unit tests for the sql variant, you can skip them using `-DskipTests`) and then run `mvn verify -Plocal-sql -Psmoke`.


## ITs with infrastructure in Kubernetes/Openshift

The Integration Tests testsuite can be configured to expect Apicurio Registry, and it's required infrastructure, to be deployed externally in a K8s cluster. 

In this mode, the testsuite expects your kubeconfig file to be already configured pointing to the cluster that will be used for testing. The tests that will be executed are determined using the maven profile just as when running locally.
As for the storage variant, it will be determined using a similar approach as the one used for the local execution but with different names: remote-mem, remote-sql, remote-mssql, remote-kafka.

We have make goals for all the deployment variants and you have examples for all the execution possibilities in our [Github Actions Workflows](.github/workflows/integration-tests.yaml)


## Integration Tests testsuite internal details

The Integration Tests testsuite is written in Java and we use JUnit 5 .

The main entry point for the testsuite is this class [`integration-tests/src/test/java/io/apicurio/deployment/RegistryDeploymentManager.java`](integration-tests/src/test/java/io/apicurio/deployment/RegistryDeploymentManager.java).

This is the class that, when running in remote mode, is responsible for deploying all the required infrastructure in K8s and making sure it remains available during the tests execution.
