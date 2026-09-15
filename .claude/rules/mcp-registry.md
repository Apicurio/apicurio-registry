---
paths:
  - "app/src/main/java/io/apicurio/registry/mcpregistry/**/*.java"
  - "app/src/main/resources-unfiltered/META-INF/resources/api-specifications/mcp-registry/**"
  - "app/src/test/java/io/apicurio/registry/noprofile/mcpregistry/**/*.java"
  - "app/src/test/java/io/apicurio/registry/auth/McpRegistryAuth*.java"
  - "schema-util/common/src/main/java/io/apicurio/registry/**/McpServer*.java"
---
# MCP Registry API

Implements the official [MCP Registry API](https://github.com/modelcontextprotocol/registry)
(`/v0.1/servers/...`) as a **second API surface** over ordinary registry artifacts, so that any client
speaking the official API works against Apicurio unchanged — with Apicurio's governance on top.
Tracks issue #7763.

## Contract audit update (2026-09-15)

The following verified behavior supersedes older limitations discussed below:

- Single-server responses and list entries use `{server, _meta}`. Publisher metadata
  stays in `server._meta`; registry metadata is at the response envelope level.
- Standard `%2F`-encoded namespace/server names work through a narrowly scoped
  pre-matching rewrite (`McpRegistryEncodedNameFilter`). There is no global encoded-
  slash setting; rewritten identities still pass `McpServerName` validation.
- `websiteUrl` is camelCase. The foreign OpenAPI source generates the JAX-RS models;
  the generic Registry v3 SDK specification has not changed.
- Configured global/group/artifact rules run on publish, in addition to mandatory
  structural validation. The MCP_SERVER compatibility checker itself remains #9913.
- Reads, status changes, deletes and publishing an additional version verify the
  artifact type; other artifact families cannot be accessed through these routes.
- `include_deleted` is implemented on reads/lists, defaults to false, and is
  effectively true for incremental listing. Version lists are newest-first.
- Filtered listing retains the documented O(N) scan limitation.

Remaining compatibility limitations: omitted `version` currently selects latest
rather than all versions, optional PUT is unsupported, hard delete returns 204,
server-wide status changes are non-atomic and return one representative response
rather than upstream's aggregate, and statusMessage is validated but not persisted.
The simplified manifest validator is not complete upstream JSON Schema validation.
Do not claim unrestricted drop-in compatibility.

Not to be confused with the `mcp/` module, which is an MCP **server** exposing registry operations as
tools. Different thing entirely; do not touch it from here.

## Where this sits

The **Iceberg REST Catalog** (`app/.../iceberg/rest/v1/`) is the structural precedent: a foreign spec,
code-generated into JAX-RS interfaces, implemented against `RegistryStorage`, gated behind an
experimental flag. Follow it when in doubt.

```
app/src/main/resources-unfiltered/.../api-specifications/mcp-registry/v0/openapi.json   ← source of truth
        │  apicurio-codegen-maven-plugin (execution in app/pom.xml)
        ▼
app/target/generated-sources/jaxrs/.../mcpregistry/rest/v0/{ApisResource,beans/*}       ← generated, not committed
        │  implements
        ▼
app/src/main/java/io/apicurio/registry/mcpregistry/
        ├── McpRegistryConfig.java      feature flag + page cap
        ├── McpServerName.java          name ↔ (group, artifact) mapping + validation
        ├── McpRegistryCursor.java      opaque cursor ↔ storage offset
        └── rest/v0/impl/McpRegistryApiResourceImpl.java
        │  RegistryStorage
        ▼
MCP_SERVER artifacts  (type + validator live in schema-util/, registered in StandardArtifactTypeProviderRegistry)
```

**The spec is the source of truth.** Never hand-edit the generated interface or beans. To change the
API, edit `openapi.json` and re-run `./mvnw generate-sources -pl app`, then adjust the impl to match
the new interface.

## Identity mapping

A server name is reverse-DNS plus a server id, separated by exactly one slash:

| MCP | Apicurio |
|---|---|
| `io.github.user/weather` | group `io.github.user`, artifact `weather` |
| server version | artifact version |
| `server.json` body | artifact content, stored **verbatim** |
| status active / deprecated / deleted | `VersionState` ENABLED / DEPRECATED / DISABLED |

**Why the paths use two params.** The spec says `/v0.1/servers/{name}/...`, but a JAX-RS template
cannot match across a `/`, and the name contains one. The vendored spec therefore declares
`/servers/{namespace}/{server_id}` — two segments instead of one.

⚠️ **This does not match the official registry's own examples**, which use a single percent-encoded
segment (`io.github.user%2Fmy-server`), confirmed against the upstream OpenAPI spec. Our two-segment
route only serves the *unencoded* form. Accepting `%2F` properly requires enabling encoded-slash
handling at the container level (Undertow/RESTEasy reject it by default, for the same reason path
traversal defenses do), which is a bigger, security-relevant change than a spec edit — flag to
maintainers rather than silently working around.

`McpServerName` is the only way to build a name; both factories validate against
`McpServerContentValidator.SERVER_NAME_PATTERN`, which admits no slashes and no `..` segments. Path
parameters reach storage as group/artifact ids, so never bypass it.

## `_meta` ownership

The registry owns exactly one key inside `_meta`:

```
_meta["io.modelcontextprotocol.registry/official"] = { id, publishedAt, updatedAt, isLatest, status }
```

- **Recomputed on every read** from version metadata — never stored.
- **Stripped from publish input**, so a publisher cannot spoof `status` or `id`.
- Every other `_meta` key belongs to the publisher and round-trips untouched.

`publishedAt`/`updatedAt` map to the version's `createdOn`/`modifiedOn`, so `publishedAt` is frozen
at first publish while `updatedAt` moves on each mutation.

**All JSON body fields are camelCase**, matching the official spec exactly (`registryType`,
`registryBaseUrl`, `fileSha256`, `runtimeHint`, `nextCursor`, `mimeType`, …) — confirmed field-by-field
against the upstream OpenAPI document, since an earlier snake_case draft would have silently broken
compatibility with real clients despite passing every local test. Query parameters stay snake_case
(`updated_since`, `include_deleted`), matching the official convention of camelCase bodies over
snake_case query strings. `StatusUpdate` also carries an optional `statusMessage` — at most 500 characters for every status,
and not allowed at all with `status: "active"`, both rejected with 400. It is accepted and validated,
but **not yet persisted or returned**; there's no slot for it in version metadata today. Real gap, not a lie: don't
claim round-trip support for it without adding storage.

Generated beans initialise list fields to empty lists, which would emit `"packages": []` for a server
that declared none. `normalize()` nulls empty lists so responses carry only what the publisher sent.

## Pagination

The spec mandates cursor pagination; storage offers offset/limit. `McpRegistryCursor` bridges them by
encoding `offset + SHA-256 fingerprint of the active filters`. A cursor presented with different
filters is rejected with 400 rather than silently returning a page of an unrelated result set.

**Ordering must be a total order, or offset paging breaks.** `listServers` orders by `OrderBy.name`,
which holds the full server name and is unique. It must *not* order by `artifactId`: that is only the
server id half, so `io.github.alice/weather` and `io.github.bob/weather` tie, and paging over a tie
silently skips some rows and repeats others. `listServerVersions` orders by `globalId` for the same
reason — `createdOn` ties for versions published in the same millisecond.

`updated_since` is parsed as an RFC 3339 timestamp by a query-specific converter. It filters and
sorts by the resolved version's `_meta.updatedAt`, not the artifact timestamp (status changes only
update the former). Equal timestamps are ordered by full server name. `search` matches the resolved
document's name OR description, case-insensitively, before pagination and page counting.

**Filtered `listServers` is an O(N), N+1 scan.** Existing storage queries cannot express this version
projection and predicate. Candidate artifacts are read in capped batches, filtered, sorted and then
paged in memory. The page cap bounds the response and each batch, not total work or memory. Unfiltered
listing keeps the existing storage-page path. A batched storage projection is a performance follow-up;
do not restore artifact-timestamp filtering as an optimization. Offset cursors are not snapshots and
can still skip/repeat rows when records change between requests.

`limit` below 1 is a 400; both explicit and omitted limits are capped at `max-page-size`. The comparison is done as a `BigInteger`,
because `intValue()` keeps only the low 32 bits — `limit=4294967298` used to become a page of 2.

## Authorization

Every path-addressed endpoint uses `@Authorized(style = GroupAndArtifact)`, which works because
`AbstractAccessController` reads the group and artifact from **method parameters 0 and 1** — exactly
where `namespace` and `serverId` sit. Keep that parameter order when editing the spec.

**`publishServer` is the exception and needs care.** The server name arrives in the request body, not
the path, so it must use `AuthorizedStyle.None` — and `AbstractAccessController.isOwner()` returns
`true` for any style it does not recognise. Owner-only authorization is therefore enforced by hand in
`verifyPublishOwnership()`, mirroring `AuthorizedInterceptor`: admins exempt, unknown artifact allowed
(nothing to own yet), null owner allowed. **If you add another body-addressed write endpoint, it needs
the same treatment.**

## Content validation

`publishServer` calls `validateServerDefinition()`, which invokes `McpServerContentValidator` directly
via `ArtifactTypeUtilProviderFactory` at `ValidityLevel.FULL`, mapping `RuleViolationException` to 400
with the violations joined into the message.

**Do not replace this with `rulesService.applyRules()` alone.** That path only fires when an operator has
configured a VALIDITY rule, which is not the default — the validator was originally unreachable from
publish for exactly that reason, and a `server.json` with a malformed `repository` was accepted with a
200. A well-formed document is a precondition of publishing, not something a deployment opts into.
After mandatory validation, publish also calls `rulesService.applyRules()` so configured governance
is enforced before creating the artifact/version.

Note the validator sees the *re-serialized bean*, not the raw request body, so Jackson coercion has
already happened: `"identifier": 123` arrives as `"123"` and is legitimately valid. The validator
catches structural problems that survive deserialization (missing required fields, bad URL strings),
not type mismatches.

Auto-detection is a separate path. `McpServerContentAccepter` decides whether JSON uploaded without an
explicit type is an `MCP_SERVER`, and requires `name` to match `SERVER_NAME_PATTERN`: a textual `name`
plus a `version` is also an npm `package.json` or a Helm chart. Checking only that the name contains a
`/` is not enough — a scoped npm name (`@scope/pkg`) has one.

## Version addressing

Reads accept `latest` (or no version) and resolve it to the branch tip. Mutations do not:
`requireConcreteVersion()` rejects `latest` and blank with 400 on `deleteServerVersion` and
`updateServerVersionStatus`, as `publishServer` already did. `latest` is resolved when the request runs,
so a publish landing between a client reading a version and changing it would redirect the change to a
version the client never saw.

## Error responses

The spec's error body is exactly `{"error": "..."}` as `application/json` — confirmed against the
upstream generic spec, which has no other error field. The vendored `Error` schema matches it, and every
declared error response references it. `McpRegistryExceptionMapperService` produces it, following the
Iceberg precedent. Status codes come from the same `HttpStatusCodeMap` the core mapper uses, so only
the body differs from v3. 5xx responses, and framework messages carrying a RESTEasy diagnostic code
(`RESTEASY003650: ...`), return the reason phrase only.

**It is dispatched from two places.** `RegistryExceptionMapper.isMcpRegistryEndpoint()` covers
everything that reaches it, including 401 and 403, whose mappers delegate to it.
`JacksonJsonMappingExceptionMapper` is chosen by exception type and never reaches that dispatch, so it
repeats the check through `McpRegistryExceptionMapperService.handles()`. Any other `ExceptionMapper`
that can fire on an MCP path needs the same.

## Feature gating

Both properties live in `McpRegistryConfig`; `enabled` is `@Info(experimental = true)`, so it also
requires `apicurio.features.experimental.enabled`.

```
apicurio.mcp-registry.enabled        default false
apicurio.mcp-registry.max-page-size  default 100
```

Every endpoint calls `requireEnabled()` first, which 404s when off — the API is invisible, not
forbidden. There is no central filter, so `McpRegistryFeatureGateTest` covers all eight endpoints and
asserts the `MCP Registry API is disabled` message, not only the 404 a missing server also returns.
After touching either property, regenerate the config docs:
`./mvnw clean install -pl :apicurio-registry-config-generator -am -DskipTests` and commit
`ref-registry-all-configs.adoc`.

## Testing

| Class | Profile | Covers |
|---|---|---|
| `McpRegistryApiTest` | experimental on, no auth | publish/read/list/versions/status/delete, cursor, validation |
| ↳ `testCursorPaginationAcrossNamespacesSharingAServerId` | | regression: paging must not skip or repeat when server ids tie |
| ↳ `testPublishRejectsRepositoryWithoutUrl` / `...RemoteWithNonHttpUrl` | | regression: the validator actually runs on publish |
| ↳ `test*UsesTheSpecErrorShape`, `testUnsupportedMethodDoesNotLeakFrameworkDetail` | | error body is `{"error"}` with no class name, on both dispatch paths |
| `McpRegistryAuthTest` | RBAC + owner-only, basic auth | ownership on publish, delete and both status updates; admin exemption; anonymous |
| `McpRegistryFeatureGateTest` | defaults | all 8 endpoints 404 with the "disabled" message |
| `McpRegistryCursorTest` | plain JUnit | cursor encode/decode/tamper |
| `McpServerContentValidatorTest` | plain JUnit | validator, accepter, extractors (fixtures in `src/test/resources/.../mcpserver-*.json`) |

**Any `@QuarkusTest` extending `AbstractResourceTestBase` under an auth-enabled profile must override
`createRestClientV3` with admin credentials.** `beforeEach` clears global rules through that client;
without credentials every method in the class fails in setup and each one burns the full retry budget
(observed: a 3-second class taking 15 minutes).

`@QuarkusTest` methods share one registry instance, so tests generate unique namespaces and search
markers to avoid colliding with each other's artifacts.

**A pagination test that publishes into one namespace proves nothing about ordering.** `testCursorPagination`
uses distinct server ids in a single namespace, so nothing ties and it passed throughout the period when
paging was demonstrably skipping rows. Ordering regressions only surface when server ids collide across
namespaces — vary the namespace, hold the server id fixed.

## Known gaps

Open questions for maintainers rather than settled decisions — raise on #7763, don't quietly pick:

- **Filtered list scalability.** Name/description and version timestamp filtering currently require
  the scan described above. A storage-side projection must preserve those semantics and total ordering.

- ~~**`_meta.id` is the artifact `globalId`, not a UUID.**~~ **Resolved.** A UUID is minted at publish
  time and persisted as an artifact-version label (`SERVER_VERSION_ID_LABEL`), the same pattern the
  Iceberg REST Catalog uses for `table-uuid`. `serverVersionId()` reads the label back on every
  request and falls back to `globalId` only for versions published before this label existed, so old
  data doesn't break. See `McpRegistryApiResourceImpl.serverVersionId()`.
- **`metadata.count` is the page size, not total matches.** The spec does not pin this down.
- **No compatibility checker for `MCP_SERVER`, deliberately for now.** It gets the builder default,
  `NoopCompatibilityChecker`, like eight other types (AsyncAPI, GraphQL, WSDL, XML, …), so a
  COMPATIBILITY rule on an `MCP_SERVER` artifact accepts every change. Designing the rules is tracked in
  #9913, for a separate PR. Note for that PR: MCP publish does not run configured rules at all — it calls
  the content validator directly (see Content validation) — so a checker takes effect on MCP publish
  only once publish also applies configured rules.
- **`PATCH /{name}/status` is not atomic.** No bulk state change exists in `RegistryStorage`, and a
  REST-level transaction would not span the Kafka-backed variants. It loops; a mid-loop failure leaves
  earlier versions changed. Safe to retry — setting an already-set state is a no-op.
- **Storage variants.** Verified end-to-end against **sql** and **kafkasql** (full lifecycle: publish,
  read, list, versions, status, soft-delete/restore, hard delete). All four variants route artifact
  search through `SqlSearchRepository` — kafkasql via `ReadOnlyDelegatingStorage`, gitops and
  kubernetesops via `Blue`/`GreenSqlStorage`, both `extends AbstractSqlRegistryStorage` — so
  `OrderBy.name` resolves identically everywhere. Write-rejection is verified on both gitops and
  kubernetesops: 501, which the spec declares for a registry that does not support deletion or
  publishing — not 403, since an admin is refused too, and not an unmapped 500.

  **Reads are confirmed broken, not just untested, on gitops and kubernetesops.** Every MCP read that
  resolves "latest" (`GET /servers/{namespace}/{server_id}` with no version, `listServerVersions`'s
  `isLatest` flag) goes through `getBranchTip(ga, BranchId.LATEST, ...)`, which is a pure
  `branch_versions` table lookup with no fallback (`CommonSqlStatements.selectBranchTip()`). The
  gitops/kubernetesops loader (`AbstractPollingDataSourceManager.processArtifact()`) imports each
  version via `storage.importArtifactVersion(entity)` only — a raw insert into `versions` — and never
  calls `appendVersionToBranch(ga, BranchId.LATEST, version)`. Confirmed by hand: a fixture loaded
  through either backend shows up in `getArtifactIds()` and `getArtifactVersionContent()` (queried
  directly, by explicit version) but `getBranchTip(..., LATEST, ...)` throws
  `VersionNotFoundException` for it regardless of state. `GitOpsSmokeTest` / `KubernetesOpsSmokeTest`
  never caught this because neither ever reads by "latest" — both only read by an explicit version
  string. **This is not an MCP-specific bug** — it affects anything built on `BranchId.LATEST` against
  gitops/kubernetesops-sourced content — but MCP is the first consumer that actually exercises that
  path against those two backends. `SqlDataUpgrader` (the v2→v3 import path) hits the same
  raw-import gap and works around it by calling `appendVersionToBranch` by hand right after
  `importArtifactVersion`; `AbstractPollingDataSourceManager` never got the equivalent fix. Tracked
  as a separate storage-layer issue, not fixed here — it's a shared-loader change with blast radius
  across every artifact type on both backends, not something to bundle into the MCP feature.
- **`GET /servers/{namespace}/{server_id}`** exists beyond the endpoint table in #7763. It is in the
  official spec, but call it out in review so it does not read as scope drift.
- **`%2F`-encoded names are not accepted.** See the callout under Identity mapping — the official
  registry's own path examples use a single percent-encoded segment, which this two-segment route
  cannot serve. Fixing it properly means enabling encoded-slash handling at the container level, a
  security-relevant change beyond this PR's scope.
- **`statusMessage` is accepted and validated but not persisted.** No slot exists for it in version
  metadata; a client that reads it back after setting it will not find it. `PUT` (admin edit-in-place)
  and `include_deleted` (on the list endpoints) are also unimplemented — both are spec-optional, so
  neither blocks compatibility, but `include_deleted` semantics are worth confirming against the
  *official* registry specifically before deciding whether to add it, since the generic sub-registry
  spec leaves default behavior for deleted servers unstated. Confirmed by hand: `PUT` returns 405, and
  `include_deleted=true` and `=false` return byte-identical results — the parameter is accepted and
  silently ignored, which is worth deciding on rather than leaving as a no-op.
- **Offset paging under concurrent writes can skip or repeat.** Version timestamp ties now have a
  name tiebreaker, but the cursor does not pin a snapshot across requests.
- ~~**Error responses expose exception class names.**~~ **Resolved.** MCP requests have their own
  mapper and return the spec's error body; see Error responses.
