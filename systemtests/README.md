# Apicurio Registry system tests

System tests for Apicurio Registry and Apicurio Registry operators.

## Necessary environment variables

Necessary environment variables for run of system tests. Other environment variables are optional and have default values. See [Constants.java](/systemtests/src/main/java/io/apicurio/registry/systemtest/framework/Constants.java) for list of supported environment variables and their default values.

|Environment variable|Purpose|
|---|---|
|`APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_IMAGE`|Path/URL to image for CatalogSource of Apicurio Registry OLM operator|
|`APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_PACKAGE`|Package name for Subscription of Apicurio Registry OLM operator|
|`APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_CHANNEL`|Channel for Subscription of Apicurio Registry OLM operator|
|`APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_STARTING_CSV`|StartingCSV for Subscription of Apicurio Registry OLM operator|
|`TESTSUITE_DIRECTORY`|Path to `systemtests` directory of this test suite|

## Known issue

You will probably hit issue with Apicurio Registry API model due to [fix](https://github.com/Apicurio/apicurio-registry-operator/commit/b78b24fdda55f3143c429f19ca62cdbc2fa7d483) that was introduced in the last weeks. You need to build API model locally and add it into project Libraries.
