# Confluent Schema Registry Compatibility API

Apicurio Registry provides a compatibility API layer (`/apis/ccompat/v7` and `/apis/ccompat/v8`)
that allows Confluent Schema Registry clients to work with Apicurio Registry.

## Supported API Versions

| Version | Path | Status |
|---------|------|--------|
| v7 | `/apis/ccompat/v7` | ✅ Fully supported |
| v8 | `/apis/ccompat/v8` | ✅ Fully supported |

## Feature Support Matrix

### ✅ Fully Supported Features

| Feature | Endpoints | Notes |
|---------|-----------|-------|
| **Schemas** | `GET /schemas`, `GET /schemas/ids/{id}`, `GET /schemas/types`, etc. | Full support including pagination |
| **Subjects** | `GET/POST/DELETE /subjects/*` | Full CRUD with pagination |
| **Compatibility** | `POST /compatibility/subjects/{subject}/versions/*` | With `verbose` and `normalize` params |
| **Config** | `GET/PUT/DELETE /config`, `GET/PUT/DELETE /config/{subject}` | Including `defaultToGlobal` |
| **Mode** | `GET/PUT/DELETE /mode`, `GET/PUT/DELETE /mode/{subject}` | Including `force` param |
| **Contexts** | `GET /contexts` | Returns default context |

### ⚠️ Partially Supported Features

| Feature | Status | Details |
|---------|--------|---------|
| **Data Contracts (metadata/ruleSet)** | Passthrough | Fields are accepted in requests but NOT enforced or stored. Rules are NOT executed. |
| **X-Confluent-Accept-Unknown-Properties** | Supported (v8) | Unknown JSON properties are ignored when header is `true` |

### ❌ Not Supported Features

These features are **not implemented** and will return errors or empty results:

| Feature | Endpoints | Behavior | Reason |
|---------|-----------|----------|--------|
| **Schema Linking / Exporters** | `/exporters/*` | `GET /exporters` returns `[]`; other operations return `operationNotSupported` error | Enterprise feature - requires cluster-to-cluster replication infrastructure |
| **KEKs (Key Encryption Keys)** | `/dek-registry/v1/keks/*` | Not present (404) | Client-side encryption feature |
| **DEKs (Data Encryption Keys)** | `/dek-registry/v1/keks/{name}/deks/*` | Not present (404) | Client-side encryption feature |
| **Cluster Metadata** | `/v1/metadata/id`, `/v1/metadata/config` | Not present (404) | Confluent cluster identification |

## Implementation Notes

1. The API follows the [Confluent Schema Registry API specification](https://docs.confluent.io/platform/current/schema-registry/develop/api.html)

2. **Schema Types**: AVRO, JSON Schema, and Protobuf are supported (not just Avro)

3. **Soft Delete Behavior**: Schemas deleted using `DELETE /subjects/{subject}/versions/{version}`
   should still be accessible using global ID. This guarantee cannot be fully kept in Apicurio's
   implementation since any schema must be part of an artifact.

4. **Validation Errors**: Some endpoints expect error `422` for invalid schemas and error `409`
   for incompatible schemas. Schema format validation is only performed if a validation rule is configured.
   When creating a new subject, a validation rule is automatically configured.

5. **State API**: If the state of an artifact (or version) is `DISABLED` or `DELETED`,
   it takes precedence and the schema is not returned.

## Migrating from Confluent Schema Registry

When migrating from Confluent Schema Registry to Apicurio Registry:

1. **Basic Operations**: Schema registration, retrieval, and compatibility checks work identically
2. **Exporters/Schema Linking**: Must use Apicurio's native import/export tools instead
3. **Data Contracts**: Metadata and rules are accepted but not enforced - use Apicurio's native rules instead
4. **Encryption (KEK/DEK)**: Must implement encryption at the application layer

## See Also

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Confluent Schema Registry API Reference](https://docs.confluent.io/platform/current/schema-registry/develop/api.html)
- [Data Contracts in Confluent](https://docs.confluent.io/platform/current/schema-registry/fundamentals/data-contracts.html)
