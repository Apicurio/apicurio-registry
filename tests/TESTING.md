[![CircleCI](https://circleci.com/gh/Apicurio/apicurio-registry.svg?style=svg)](https://circleci.com/gh/Apicurio/apicurio-registry)

# Apicurio Registry System and Integration Tests

## Prerequisites
* Installed [GraalVM](https://www.graalvm.org/docs/getting-started/)
* Set `$PATH` and `$GRAALVM_HOME` variables to GraalVM binaries
* Installed `native-image` by command `gu install native-image`
* ``Native image -> apicurio-registry-app-1.0.0-SNAPSHOT-runner`` !without suffix .jar!

## Before test execution
Before your first run, you have to build jar of apicurio-registry-app. For build you can use the following command:

```./mvnw clean install```

## Run tests
For run all tests in `tests` package you can use the following command:

```./mvnw verify -pl tests -Pall -Dmaven.javadoc.skip=true```

If you want to run only specific tests, you can specify it by `-Dit.test` maven option.

If you want to run only specific tag, you case run `smoke` or `cluster` profiles.

When you want to execute tests from InteliJ you need to specify native-image path to your configuration
-
```edit configuration -> template -> junit -> VMoptions (append with path to your native image)```

