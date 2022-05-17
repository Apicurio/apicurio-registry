# Apicurio Registry system tests

System tests for Apicurio Registry and Apicurio Registry operators.

## Environment variables

Environment variables needed for run of system tests. Other environment variables are optional and have default values. See [Environment.java](/systemtests/src/main/java/io/apicurio/registry/systemtest/framework/Environment.java) for list of supported environment variables and their default values.

|Environment variable|Purpose|
|---|---|
|`CATALOG_IMAGE`|Path to an index image for an Operator catalog (if you want to use it)|
|`CONVERTERS_SHA512SUM`|SHA-512 sum of Apicurio converters used in KafkaConnect|
|`CONVERTERS_URL`|Path to Apicurio converters used in KafkaConnect|

## Known issues

You will probably hit issues with Apicurio Registry API model due to fixes that were introduced in the last weeks. You need to build API model locally and add it into project Libraries:
- [Apicurio/apicurio-registry-operator PR#169](https://github.com/Apicurio/apicurio-registry-operator/pull/169)
- [Apicurio/apicurio-registry-operator PR#173](https://github.com/Apicurio/apicurio-registry-operator/pull/173)
- [Apicurio/apicurio-registry-operator PR#174](https://github.com/Apicurio/apicurio-registry-operator/pull/174)
