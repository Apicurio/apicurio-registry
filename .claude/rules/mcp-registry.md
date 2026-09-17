---
paths:
  - "app/src/main/java/io/apicurio/registry/mcpregistry/**/*.java"
  - "app/src/main/resources-unfiltered/META-INF/resources/api-specifications/mcp-registry/**"
  - "app/src/test/java/io/apicurio/registry/noprofile/mcpregistry/**/*.java"
  - "app/src/test/java/io/apicurio/registry/auth/McpRegistryAuth*.java"
  - "schema-util/common/src/main/java/io/apicurio/registry/**/McpServer*.java"
---
# MCP Registry API

Tracks #7763. This is a foreign REST surface at `/apis/mcp-registry/v0.1`, not the
`mcp/` module's MCP tools. Upstream contract:
https://github.com/modelcontextprotocol/registry/blob/739b70e8bc1bea203c5a35ab699f1df51d091568/docs/reference/api/openapi.yaml

## Source of truth and validation

- `app/src/main/resources-unfiltered/META-INF/resources/api-specifications/mcp-registry/v0/openapi.json`
  generates JAX-RS interfaces/beans during the Maven build. Never edit generated files.
- `McpServerContentValidator` uses the pinned **2025-12-11 draft-07 server.json schema**
  bundled in schema-util/common. All assertion keywords match the upstream dated
  schema; explanatory annotations are omitted. See its provenance/license document.
- `McpServerRequestReader` validates raw JSON before bean conversion, preventing
  Jackson scalar coercion from accepting invalid types. No publisher schema URL is
  fetched. Body size is limited to 1 MiB in addition to framework limits.
- Publish validates content unconditionally, then applies configured global/group/
  artifact rules before writing. `McpServerCompatibilityChecker` protects existing
  install/connection choices; package version/hash upgrades are allowed. Registry
  compatibility policy is independent of the upstream discovery protocol.
- The generic v3 OpenAPI/SDK model is unaffected by changes to this foreign API.

## Identity and metadata

`io.github.user/weather` maps to group `io.github.user`, artifact `weather`.
`McpServerName` validates identifiers and excludes traversal dot segments.
Routes declare the single encoded `{serverName}` segment directly, matching upstream.
`AuthorizedStyle.McpServerName` validates/splits parameter 0 for owner checks. The
former rewrite filter and two-segment aliases are removed; no global encoded-slash
switch is enabled. Internal Java/spec directory `v0` is the facade generation, not
another served API: the wire version stays `v0.1`.

Single-version responses and list entries are `{server, _meta}`. Publisher metadata
is inside `server._meta`. Registry-owned lifecycle fields are in envelope
`_meta["io.modelcontextprotocol.registry/official"]`; Apicurio's UUID extension is
in `_meta["io.apicurio.registry"].id`, not an extra property in the upstream block.
Version UUID labels use `apicurio.mcp-registry.version-id` with legacy-key fallback.
Status maps ENABLED/DEPRECATED/DISABLED to active/deprecated/deleted. Drafts are not
published MCP records and cannot be discovered or promoted through the MCP API.

`statusMessage` (max 500 characters) is supported for every status, including active.
Descriptive status message/time hints are stored under `apicurio.mcp-registry.status.*`.
They are user-editable metadata, never an authorization or approval source. Actual
status is always derived from version state. Omitting a message clears the old hint.

## Reads and pagination

- Omitted `version` lists all published versions. `version=latest` selects one per
  server; an exact version selects that version where present.
- `include_deleted=false` by default; incremental `updated_since` listings always
  include deleted records. The selected inclusion policy also controls isLatest.
- Version lists are newest-first. Cursors bind offset to filters; do not reuse a
  cursor with other search/version/timestamp/deletion options.
- Search matches name OR description on the returned version. `updated_since` is
  RFC3339 and compares that version's `modifiedOn`, not artifact modification time.
- Both default and supplied limits are capped. Filtering happens before response
  pagination, preventing missed matches and incorrect page counts.
- Current implementation scans candidates in capped batches, resolving and sorting
  matches in memory. This is O(N) candidate work and O(matches) memory, **not** bounded
  by response page size. This implementation characteristic does not authorize
  silently truncating results. Offset cursors are not snapshots under concurrent writes.

## Mutation atomicity and authorization

Every addressed route verifies MCP_SERVER type before reading/mutating content.
Path parameter 0 is the full decoded server name for `@Authorized(McpServerName)`.
Publish is body-addressed and explicitly verifies owner-only access; anonymous and
non-owner access are covered by auth tests.

`RegistryStorage.updateArtifactVersionStates` atomically updates selected versions
and descriptive labels. SQL nests updates in one outer HandleFactory transaction;
KafkaSQL journals a single message invoking that operation. Read-only implementations
reject it. Search-index/outbox decorators must remain wired when changing this API.
Rollback tests inject a DB constraint failure on the **second** version and assert
both complete metadata snapshots are restored, for SQL and KafkaSQL.

Server-wide PATCH returns `{updatedCount, servers}`. DELETE sets DISABLED and returns
HTTP 200 with the retained record, permitting discovery of tombstones and restoration.
Permanent removal stays behind native v3's artifact-version deletion gate. Optional
PUT returns the upstream-permitted 501 because
in-place edits of immutable server versions are unsupported. These are separate
from soft deletion (PATCH deleted). Read-only storage returns 501 for writes.

The polling loader imports the latest branch for successfully imported non-DRAFT
versions so GitOps/KubernetesOps latest reads work; do not call public branch
mutation methods that reject system-defined branches to build these entries.

## Tests and feature gates

Both `apicurio.features.experimental.enabled` and `apicurio.mcp-registry.enabled`
must be true. `ExperimentalFeaturesConfig` is eagerly instantiated with `@Startup`;
the startup-failure test proves the check runs. Defaults remain off. `max-page-size`
defaults to 100; both MCP config properties are availableSince 3.4.0.

`McpRegistryExperimentalStartupTest` uses QuarkusUnitTest, which cannot share a JVM
with QuarkusTest. It has tag `experimental-startup`, excluded from default-test and
default-cli executions, and runs in its own `experimental-startup-test` execution.
To invoke it directly use `surefire:test@experimental-startup-test` after compilation.
Functional tests use `McpRegistryRequests` to encode readable fixture coordinates;
the helper preserves complete resolved URLs and already-encoded query values.

- `McpRegistryApiTest`: publish/read/list/status/delete, metadata envelopes, paths.
- `McpRegistryConformanceTest`: schema failures, raw types, drafts, pagination,
  compatibility enforcement, aggregate results and transaction rollback.
- `McpRegistryListRegressionTest`: response caps, timestamps, search and tombstones.
- `McpRegistryGovernanceTest`: rule invocation/rejection before writes, type isolation.
- `McpRegistryAuthTest`: owner/non-owner/anonymous and encoded-path authorization.
- `McpRegistryKafkaSqlLifecycleTest`: real journal lifecycle and second-version rollback.
- GitOps/KubernetesOps smoke tests: latest-branch read regression; MCP write tests: 501.
- `McpServerContentValidatorTest`: full pinned-schema validation and metadata utilities.

Use unique test coordinates; auth-enabled AbstractResourceTestBase subclasses must
provide authenticated admin clients for inherited rule cleanup. Quarkus tests that
mock RulesService are not evidence of real compatibility semantics; keep the real
rule integration test in McpRegistryConformanceTest.
