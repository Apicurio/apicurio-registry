---
paths:
  - "app/src/test/**/*.java"
  - "integration-tests/**/*.java"
  - "**/src/test/**/*.java"
---
# Testing Conventions

## Unit Tests
- Use `@QuarkusTest` for tests needing CDI/Quarkus context
- Place in the same module under `src/test/java/`
- Run: `./mvnw test -pl <module>`

## Integration Tests
- Located in `integration-tests/` module
- Base profile: `integration-tests` (required to build/run this module)
- Optional Kubernetes deployment profiles: `remote-mem`, `remote-sql`, `remote-kafka`, `remote-kubernetesops`
- Run: `./mvnw verify -Pintegration-tests -pl integration-tests -am`

## Storage Variant Coverage
- Features touching storage MUST work across all variants (sql, kafkasql, gitops, kubernetesops)
- The `sql` variant is primary; others replicate via different mechanisms
- Test at minimum with `sql` and `kafkasql` profiles

## Assertions
- Use JUnit 5 assertions or Hamcrest matchers
- The Java `assert` keyword is banned (checkstyle: IllegalToken for LITERAL_ASSERT)
- Use Awaitility for async/eventual consistency checks
