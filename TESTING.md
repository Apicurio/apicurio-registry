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

Located under `integration-tests`. We have a set of tests for the current version of Apicurio Registry located in `integration-tests/testsuite` and another set of tests used to test the compatibility of old clients with the current implementation of Apicurio Registry old API, this "legacy" compatibility tests are located in `integration-tests/legacy-tests` and are only executed in GitHub Actions as part of our [CI](.github/workflows/integration-tests.yaml).

This set of tests are mainly designed to work in two different modes:

+ Apicurio Registry and required infrastructure deployed locally (processes, docker containers, mocks, ...) by the testsuite
+ Apicurio Registry and required infrasturcture are deployed externally and connection details have to be provided in order to execute the tests.

### ITs with local infrastructure

This is the normal mode used when you execute the testsuite. Because Apicurio Registry supports various storage backends and various deployment time configurations(such as multitenancy, authentication, clustering,...) this tests deploy different components depending on the configuration provided.

The configuration is provided via maven profiles. You can find all the available maven profiles [here](integration-tests/testsuite/pom.xml)
When executing the testsuite you normally provide two profiles:
+ test profile (which determines the tests that will be executed), some options are acceptance , multitenancy ,...
+ storage variant to test (which determines the storage backend that will be deployed, and therefore tested), the available options are: inmemory , sql , kafkasql .

You can find multiple examples of how to run the testsuite in this mode in our [Github Actions Workflows](.github/workflows/integration-tests.yaml)

As you may have noticed in our Github Actions Workflows, this testsuite mode depends on the rest of the project to be built first, in order to have the application jars available or the serdes module to be available as well.

Also we have several make goals for running the tests i.e:
```
make run-sql-integration-tests
```


## ITs with infrastructure in Kubernetes/Openshift

The Integration Tests testsuite can be configured to expect Apicurio Registry, and it's required infrastructure, to be deployed externally. That can be Kubernetes/Openshift or somewhere else.

The testsuite accepts environment variables to configure this mode. The environment variables used are:
+ EXTERNAL_REGISTRY , boolean value to enable/disable this mode, false by default.
+ REGISTRY_HOST , host to access externally deployed Apicurio Registry
+ REGISTRY_PORT , port to access externally deployed Apicurio Registry

For this purpose the best example can be found in our [Github Actions Workflows](.github/workflows/kubernetes-tests.yaml)

We have another project in order to run Kubernetes/Openshift tests that combine testing for [Apicurio Registry Operator](https://github.com/Apicurio/apicurio-registry-operator) and functionality testing for [Apicurio Registry](https://github.com/Apicurio/apicurio-registry-operator). It is a [new testsuite](https://github.com/Apicurio/apicurio-registry-k8s-tests-e2e) that internally can execute the java Integration Tests that can be found in this repository. We often refer to this testsuite as the e2e testsuite.

## Integration Tests testsuite internall details

The Integration Tests testsuite is written in Java and we use JUnit 5 .

The main entry point for the testsuite is this class [`integration-tests/integration-tests-common/src/main/java/io/apicurio/tests/common/RegistryDeploymentManager.java`](integration-tests/integration-tests-common/src/main/java/io/apicurio/tests/common/RegistryFacade.java) . In that class we implemented our own custom JUnit extension that allows us to easily deploy several configurations for Apicurio Registry and required infrastructure depending on configuration parameters. The actual logic for all the deployment configurations is in this class [`integration-tests/integration-tests-common/src/main/java/io/apicurio/tests/common/RegistryFacade.java`](integration-tests/integration-tests-common/src/main/java/io/apicurio/tests/common/RegistryFacade.java)
