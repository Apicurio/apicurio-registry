# Migrating from the Java SDK v2 (REST API v2) to the Java SDK v3

The `client-v2` module (`io.apicurio.registry.client.RegistryV2ClientFactory` and
the generated REST API v2 client) is **deprecated**. It will be removed once
REST API v2 itself is removed — see
[#7330](https://github.com/Apicurio/apicurio-registry/issues/7330) (REST API v2
deprecation timeline) and
[#7336](https://github.com/Apicurio/apicurio-registry/issues/7336) (this SDK
removal tracking issue).

All new usage should target the `client` module (REST API v3 /
`RegistryClientFactory`). This guide covers the practical steps to migrate.

> **Note:** This guide covers the `client-v2` module (Kiota-generated,
> Maven artifact `apicurio-registry-v2-java-sdk`). If your application
> instead uses the legacy `apicurio-registry-client` (2.6.13.Final) product
> client, see the
> [Migrating REST API and SDK client applications](https://github.com/Apicurio/apicurio-registry/blob/main/docs/modules/ROOT/pages/getting-started/assembly-migrating-registry-v2-v3.adoc)
> section of the migration guide instead.

## 1. Update your Maven/Gradle dependency

**Before (v2 SDK):**

```xml
<dependency>
    <groupId>io.apicurio</groupId>
    <artifactId>apicurio-registry-v2-java-sdk</artifactId>
    <version>${apicurio-registry.version}</version>
</dependency>
```

**After (v3 SDK):**

```xml
<dependency>
    <groupId>io.apicurio</groupId>
    <artifactId>apicurio-registry-java-sdk</artifactId>
    <version>${apicurio-registry.version}</version>
</dependency>
```

## 2. Swap the factory and client imports

The v3 SDK mirrors the v2 SDK's factory API, so the factory swap itself —
the two import lines and the factory class name — is mechanical.
`RegistryClientOptions` itself is the same shared class in both SDKs. The
generated `RegistryClient` types differ between versions
(`io.apicurio.registry.rest.client.v2.RegistryClient` vs.
`io.apicurio.registry.rest.client.RegistryClient`), with different request
builders, so treat this step as swapping the entry point only — see section
4 for what changes in the calls made through that client.

**Before:**

```java
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.v2.RegistryClient;

RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080/apis/registry/v2");
RegistryClient client = RegistryV2ClientFactory.create(options);
```

**After:**

```java
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;

RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080/apis/registry/v3");
RegistryClient client = RegistryClientFactory.create(options);
```

## 3. Update the base URL

**This is a required step, not just an example detail above.** The base URL
passed into `RegistryClientOptions` must change from `/apis/registry/v2` to
`/apis/registry/v3`.

This matters more than it may look: `RegistryClientRequestAdapterFactory`
normalizes the URL by checking whether it already ends with the *target*
version's path (`/apis/registry/v3` when using the v3 factory). If it
doesn't, the factory appends that suffix — it does not detect or strip a
different version's suffix that's already present. `RegistryClientOptions`
enables this normalization by default (`normalizeRegistryUrl = true`). As a
result, if you switch to `RegistryClientFactory` but leave a stale
`.../apis/registry/v2` URL in place, the client does not fail fast: it
silently produces a doubled, invalid path like
`.../apis/registry/v2/apis/registry/v3` and requests will 404 at call time
instead of erroring at startup. Always update the base URL alongside the
factory swap.

`RegistryClientOptions` also carries auth, TLS, and custom headers — these
are shared unchanged between the v2 and v3 SDKs, so no changes are needed
there beyond the base URL update above.

Key differences:

| | v2 SDK | v3 SDK |
|---|---|---|
| Factory class | `io.apicurio.registry.client.RegistryV2ClientFactory` | `io.apicurio.registry.client.RegistryClientFactory` |
| Generated client package | `io.apicurio.registry.rest.client.v2.RegistryClient` | `io.apicurio.registry.rest.client.RegistryClient` |
| Base path | `/apis/registry/v2` | `/apis/registry/v3` |
| Maven artifact | `apicurio-registry-v2-java-sdk` | `apicurio-registry-java-sdk` |

## 4. Update API calls for v3 semantics

REST API v3 is not a drop-in wire-compatible replacement for v2 — some
resource paths, request/response shapes, and group/artifact semantics changed
between the two API versions. After swapping the client, review call sites
that:

- Reference artifact **groups** — group handling is more explicit in v3.
- Rely on v2-specific endpoints that have no v3 equivalent, or that were
  renamed/restructured in v3.
- Parse response payloads directly (rather than through generated model
  classes) — some field names/shapes differ.

Consult the REST API v3 OpenAPI spec (or the generated model classes in the
`client` module) for the authoritative shape of each endpoint, and adjust call
sites accordingly.

## Timeline

- The `client-v2` module and `RegistryV2ClientFactory` are marked
  `@Deprecated` starting in this release.
- The module will be removed in a future release once REST API v2 is removed,
  per the timeline tracked in
  [#7330](https://github.com/Apicurio/apicurio-registry/issues/7330).
- Track removal progress in
  [#7336](https://github.com/Apicurio/apicurio-registry/issues/7336).
