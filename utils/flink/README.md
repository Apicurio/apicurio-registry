# Apicurio Registry Flink Catalog

This module provides Apache Flink Catalog integration for Apicurio Registry,
enabling Flink SQL jobs to resolve table schemas directly from the registry.

## Concept Mapping

| Apicurio Registry | Flink Catalog |
|-------------------|---------------|
| Groups            | Databases     |
| Artifacts         | Tables        |

## Usage

### SQL DDL

```sql
-- Create a catalog pointing to Apicurio Registry
CREATE CATALOG my_registry WITH (
  'type' = 'apicurio',
  'registry.url' = 'http://localhost:8080/apis/registry/v3'
);

-- Use the catalog
USE CATALOG my_registry;

-- List databases (groups)
SHOW DATABASES;

-- Use a specific database (group)
USE my_group;

-- List tables (artifacts)
SHOW TABLES;

-- Query a table (schema resolved from registry)
SELECT * FROM my_artifact;
```

### Configuration Options

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `type` | Yes | - | Must be `apicurio` |
| `registry.url` | Yes | - | Base URL of the Apicurio Registry API |
| `default-database` | No | `default` | Default database (group) name |
| `registry.auth.type` | No | `none` | Auth type: `none`, `basic`, or `oauth2` |
| `registry.auth.username` | No | - | Username for basic auth |
| `registry.auth.password` | No | - | Password for basic auth |
| `registry.auth.token-endpoint` | No | - | OAuth2 token endpoint URL |
| `registry.auth.client-id` | No | - | OAuth2 client ID |
| `registry.auth.client-secret` | No | - | OAuth2 client secret |
| `cache.ttl.ms` | No | `300000` | Schema cache TTL in milliseconds |

### Java API

```java
import io.apicurio.registry.flink.ApicurioCatalog;
import io.apicurio.registry.flink.ApicurioCatalog.CatalogConfig;

CatalogConfig config = CatalogConfig.builder()
    .name("my-registry")
    .url("http://localhost:8080/apis/registry/v3")
    .defaultDatabase("default")
    .authType("basic")
    .username("admin")
    .password("secret")
    .cacheTtlMs(60000)
    .build();

ApicurioCatalog catalog = new ApicurioCatalog(config);
catalog.open();

// Use the catalog
List<String> databases = catalog.listDatabases();
List<String> tables = catalog.listTables("my-group");
CatalogBaseTable table = catalog.getTable(
    new ObjectPath("my-group", "my-artifact")
);

catalog.close();
```

## Supported Schema Types

| Schema Type | Supported |
|-------------|-----------|
| Apache Avro | ✅ |
| JSON Schema | ✅ |
| Protobuf    | ❌ (future) |

## Building

```bash
mvn clean install -pl utils/flink -am
```

## Testing

Unit tests:
```bash
mvn test -pl utils/flink
```

Integration tests (requires running registry):
```bash
REGISTRY_URL=http://localhost:8080/apis/registry/v3 \
  mvn test -pl utils/flink -Dtest=ApicurioCatalogIT
```
