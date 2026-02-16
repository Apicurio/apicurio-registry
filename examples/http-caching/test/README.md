# Rules for writing HTTP caching tests

## General principles

1. The tests are specific to Varnish and Varnish configuration in this example module. Running them against a different
   caching solution or configuration is not supported yet.
2. Test files:
    - The tests are contained in a single executable bash script named `run-tests.sh`.
    - There is a "wrapper" script named `setup-and-run-tests.sh` that sets up the environment using
      `docker-compose.yaml`, runs the tests, and tears
      down the environment.
    - Both files can be run by a user directly, but `setup-and-run-tests.sh` will be run by CI.
    - `run-tests.sh` must work with both `docker-compose.yaml` and `docker-compose-dev.yaml`.
3. Test files are structures to have high readability and maintainability:
    - Reusable code, such as request generation, response parsing, assertions etc. are extracted to separate functions.
      These are called from the main test flow and are reused as much as possible to keep the main test flow clear and
      concise.
    - Each "test suite" and "test cases" that are part of it are clearly marked with comments and logically and visually
      separated from each other.
4. Test files have a debug mode, that prints debugging information, such as request and response details, assertion
   results, and any relevant environment information. This can be enabled by setting a `--debug` parameter.
5. Test files have a `--help` parameter that prints usage instructions and, in case of `run-tests.sh`, *concise*
   information about the test cases.
6. Test files have a `--fail-fast` parameter that stops the execution of the tests as soon as a test case fails.
7. Tests are parametrized by configuration in `io.apicurio.registry.rest.cache.HttpCachingConfig`, so that the
   configuration can be easily updated. This does not mean that the tests should read the configuration from that file,
   but that the configuration should be easily updated in the test files when needed.
8. Each test case and it's result is clearly marked in the output, using colours and human-friendly formatting.
9. Each "test suite" section is focused on testing a specific endpoint, with multiple tests each verifying a specific
   feature.
10. Tests scripts must be able to be re-run multiple time without any manual cleanup, even if the backend is not
    restarted. This means when creating data in Registry, use randomized names/identifiers to avoid conflicts with
    existing data. Prefer creating artifacts in a `test` group.
11. Provide a parameter to run a specific test suite. Running a specific test case is not required to reduce
    complexity.
12. When adding or updating tests, make sure to check if the new changes can be applied to other already existing tests.

## How to write a test case

1. Analyze REST API endpoints to identify which have caching enabled, and write a "test suite" for each of them.
2. REST API specification is available at `common/src/main/resources/META-INF/openapi.json`.
3. Check for caching strategy that is being applied, and test for expected behaviour for different parameters for that
   strategy. For example, the endpoint `io.apicurio.registry.rest.v3.impl.IdsResourceImpl.getContentByGlobalId` uses
   `io.apicurio.registry.rest.cache.strategy.EntityIdContentCacheStrategy`. Test not only the behavior for various path
   and query parameters, but also for properties that are configured using other REST operations, such as:
    - artifact state (mainly DRAFT)
    - references, which can also be in DRAFT state
4. Take information in `app/src/main/java/io/apicurio/registry/rest/cache/README.md` into account.
5. Look at the Varnish configuration in `docker-compose.yaml` to understand how the cache is configured, and take that
   configuration into account, but avoid testing for *specific Varnish features*. The tests should be focused on testing
   the expected behavior of the caching solution.

## Considerations and limitations

To keep the tests from being too complex, assume the following (for now):

1. Registry configuration does not change, as that would require restarting the backed:
    - Artifact version mutability is enabled
2. Varnish configuration does not change, as that would require restarting the cache.
3. Only test Registry v3 REST API, as that is the only version that has caching enabled.
4. Do not test for hashed ETags feature
5. Use JSON artifacts as much as possible, as some features, such as references rewrite are not supported for some other
   artifacts.
6. Keep the generated content (artifacts) as simple and short as possible (unless required by the test case), to keep
   the script as short and readable as possible. For example, do not use a large JSON schema when a small one is
   sufficient for the test case.
