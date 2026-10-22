# Configuration Properties

## Naming

- All custom properties use the `apicurio.` prefix
- Hierarchy: `apicurio.[category].[feature].[aspect]` (2-4 levels)
- Related properties share a common prefix (e.g., `apicurio.auth.admin-override.*`)
- The suffix should reflect the expected value type:

| Suffix                       | Type                   | Example                                                      |
|------------------------------|------------------------|--------------------------------------------------------------|
| `.enabled`                   | `boolean`              | `apicurio.a2a.enabled`                                       |
| `.url`                       | `String` (URL)         | `apicurio.a2a.agent.url`                                     |
| `.kind`, `.type`             | `String` (enum-like)   | `apicurio.storage.kind`, `apicurio.auth.admin-override.type` |
| `.seconds`                   | `Integer` (duration)   | `apicurio.auth.token.expiration.seconds`                     |
| `.password`, `.username`     | `String` (credential)  | `apicurio.storage.sql.password`                              |
| `.topic`                     | `String` (Kafka topic) | `apicurio.kafkasql.topic`                                    |
| `.name`                      | `String` (identifier)  | `apicurio.a2a.agent.name`                                    |
| `.prefix`                    | `String`               | `apicurio.authn.audit.log.prefix`                            |
| `.dir`, `.path`, `.location` | `String` (filesystem)  | `apicurio.gitops.repo.dir`                                   |
| `.max-size`, `.min-size`     | `Integer` (limit)      | `apicurio.storage.metrics.cache.max-size`                    |
| `.level`                     | `String` (log level)   | `apicurio.log.level`                                         |

## Default Values

Prefer setting defaults in the `@ConfigProperty` annotation rather than `application.properties`:

```java
// Preferred — default is visible at the declaration site and picked up by doc generation
@ConfigProperty(name = "apicurio.example.enabled", defaultValue = "false")

// Avoid — only use application.properties when necessary (e.g., Quarkus property overrides,
// environment-variable interpolation, or dynamic allow entries)
```

Use `application.properties` only when:
- Overriding Quarkus-managed properties (e.g., `quarkus.datasource.*`)
- Referencing other properties via `${...}` interpolation (`defaultValue` does not support expressions)
- Defining `.dynamic.allow` entries for `@Dynamic` properties

## @Info Annotation

Every `@ConfigProperty` field in the `app` module must also have `@Info`:

```java
@ConfigProperty(name = "apicurio.example.enabled", defaultValue = "false")
@Info(category = CATEGORY_REST, description = "Enable example", availableSince = "3.0.0")
boolean exampleEnabled;
```

Key `@Info` fields:
- `category` (required) — from `ConfigPropertyCategory` enum (api, auth, storage, rest, etc.)
- `description` — human-readable, used in generated docs
- `availableSince` — version string
- `experimental` — marks the property as experimental (default: false)

## Experimental Features

Properties marked `@Info(experimental = true)` are gated by `apicurio.features.experimental.enabled`.
If an experimental property is enabled but the gate is not, the application fails at startup.

When adding a new experimental feature:
1. Set `@Info(experimental = true)` on its config properties
2. The `ExperimentalFeaturesConfig` validation picks it up automatically — no extra wiring needed

## Dynamic (Runtime-Updatable) Properties

Properties that can change at runtime use `@Dynamic` + `Supplier<T>`:

```java
@Dynamic(label = "UI label", description = "Help text", requires = "apicurio.other.enabled=true")
@ConfigProperty(name = "apicurio.example.setting", defaultValue = "false")
@Info(category = CATEGORY_REST, description = "...", availableSince = "3.0.0")
public Supplier<Boolean> setting;
```

Dynamic properties need a `.dynamic.allow` entry in `application.properties`.

## Doc Generation

`docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc` is **generated** by
scanning the compiled `app` JAR (Jandex) for `@ConfigProperty` + `@Info` annotations. CI verifies
it is up to date.

Properties found in `application.properties` but not via Jandex end up in an `"unknown"` category,
which is undesirable. This is why `@ConfigProperty` + `@Info` classes should live in the `app`
module (or its compile-scoped dependencies that are indexed).

**When to regenerate:** after any change to `@ConfigProperty`/`@Info` fields or `apicurio.*`
entries in `application.properties`.

```bash
./mvnw clean install -pl :apicurio-registry-config-generator -am -DskipTests
```

Then commit the updated `ref-registry-all-configs.adoc`.
