# ADR-0028: Agent inventory sync

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-19.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#10116](https://github.com/Apicurio/apicurio-registry/issues/10116),
  [#8407](https://github.com/Apicurio/apicurio-registry/issues/8407),
  [#11331](https://github.com/Apicurio/apicurio-registry/issues/11331),
  [#11336](https://github.com/Apicurio/apicurio-registry/pull/11336).
- **Scope:** Keeping a group in sync with an external inventory of agents and MCP servers
  (Kubernetes resources, Google Cloud Agent Registry). The artifact types and the rest of the agent
  registry are [ADR-0014](0014-agent-registry.md); discovery standards are
  [ADR-0004](0004-ai-catalog-ard-convergence.md); MCP tool import, which a sync can trigger, is part
  of ADR-0014.

## Context

Agents and MCP servers are often deployed and registered somewhere else: as custom resources of a
Kubernetes operator, or in a cloud vendor's agent registry. Copying them into the registry by hand
goes stale. Teams want one inventory across clusters and platforms, with the registry's versions,
rules and discovery, while the platform that runs the agents stays the source of truth. Any solution
must respect the registry's authorization and auditing, must not let an external system overwrite
artifacts it did not create, and must keep agent code out of core (ADR-0014, Decision 4).

## Decision

1. **One endpoint, run on request.** `POST /apis/registry/v3/groups/{groupId}/agent-inventory-sync`
   reads one source and reconciles the group. There is no background scheduler: the caller schedules
   it (for example a Kubernetes `CronJob`). The endpoint requires write access to the group
   (`@Authorized(GroupOnly, Write)`) and is `@Audited`; the request body is excluded from audit
   metadata because its headers may carry credentials.
2. **Writes go through the regular v3 paths.** Each item is created with
   `GroupsResourceImpl.createArtifact` (`IfArtifactExists.FAIL` for new items, `CREATE_VERSION` for
   changed ones) and state changes use `updateArtifactVersionState`, so rules, write hooks,
   authorization and auditing apply as for any caller, with the caller's identity.
3. **Pull only, read only.** An `InventorySource` lists items; the registry never writes back.
   Sources:

   | Source | Reads | Artifact ID | Type |
   |---|---|---|---|
   | `KUBERNETES_MCP_SERVERS` | `MCPServer` (`mcp.x-k8s.io`, tries `v1`, `v1beta1`, `v1alpha1`) in one namespace | `k8s-mcp.<namespace>.<name>` | `MCP_SERVER` |
   | `KUBERNETES_AGENT_CARDS` | `AgentCard` (`v1alpha1`, group configurable) `status.card` in one namespace | `k8s-agent.<namespace>.<name>` | `AGENT_CARD` |
   | `GOOGLE_AGENT_REGISTRY` | Agent Registry API v1 `.../agents` of one `projects/<p>/locations/<l>`, paged (100 per page) | `gcp-agent.<project>.<location>.<agent>` | `AGENT_CARD` |

4. **Ownership by reserved labels.** Every artifact a sync creates carries
   `apicurio.agent-inventory.source`, `.scope` (namespace or location) and `.external-id`, owned by
   `AgentInventoryLabels` (a `FeatureLabelOwner`). A sync changes an artifact only if all three match
   and, for `MISSING`, the external ID maps back to the artifact's own ID; anything else with the same
   ID is `NOT_MANAGED` and left alone. The labels identify origin and grant nothing.
5. **Reconciliation against the latest version.** Per item: `CREATED`; `UNCHANGED` when the latest
   version's content equals the item; `UPDATED` (new version) when it differs, or when a `DEPRECATED`
   latest version is listed again (it is set back to `ENABLED`); `REJECTED` with rule causes, nothing
   stored. A latest version someone set to `DISABLED` is reported `UNCHANGED` and gets no new version
   and no tool import.
6. **No deletes; missing means deprecated.** Managed artifacts of the same source and scope that the
   listing no longer contains are reported `MISSING`, and an `ENABLED` latest version becomes
   `DEPRECATED`. An `AgentCard` without a fetched card counts as listed: nothing is written, and it is
   not reported missing.
7. **All-or-nothing listing.** The full listing is read and validated before any write: a failed,
   malformed, oversized (`items.max-size`), duplicate-ID or non-terminating (repeated page token)
   listing fails the sync with `InventorySourceException` (502, or 400 when the source refuses the
   credentials or scope). Its messages are fixed sentences that never repeat the URL, headers or
   source response.
8. **Credentials stay with the source.** Kubernetes sources use the registry's service account and
   refuse request headers. The Google source sends the request's `headers` (validated by
   `OutboundHeaders`, transport headers refused) to Google only; they are not stored or returned.
9. **Behind the agents boundary.** Core reaches the feature through
   `io.apicurio.registry.extensions.AgentInventorySyncHandler`; without a handler the endpoint answers
   409. The feature is experimental: `apicurio.agent-inventory.enabled` requires
   `apicurio.features.experimental.enabled`, and when off the endpoint answers 409.

### Why no background sync

A scheduler would need a system identity that bypasses per-caller authorization and makes audit
entries anonymous. Running on request keeps every write attributable, and scheduling is trivial to
add outside the registry.

## Mapping

- **MCP servers** become a `server.json` with name `io.k8s.<namespace>/<name>`, version from
  `status.serverInfo.version` (else `0.0.0`), a description of at most 100 characters, and
  `status.address.url` as a `streamable-http` remote. With `importTools: true` (only for this source,
  and only when MCP import is enabled) the tools are read through the MCP import of ADR-0014, subject
  to its host policy; a failure is reported per item as `toolImportError`.
- **Agent cards** in A2A 0.3 form (a single `url`) are normalized to 1.0 `supportedInterfaces`
  (`AgentCards`); nothing else is added. A Google agent without a stored card gets one built from
  its display name, description, version, skills and A2A interfaces.
- `dryRun: true` returns the report without writing.

### Behaviour across storage variants

| Variant | Behaviour |
|---|---|
| SQL | Supported |
| KafkaSQL | Supported (the sync test suite runs against it) |
| GitOps, KubernetesOps | Read-only storage: refused with 409 before the source is read |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.agent-inventory.enabled` | `false` | Enable the endpoint (experimental) |
| `apicurio.agent-inventory.kubernetes.namespaces` | none (Kubernetes sources refused) | Namespaces a sync may read |
| `apicurio.agent-inventory.kubernetes.agent-card.group` | `agent.rossoctl.dev` | API group of `AgentCard`; `agent.kagenti.dev` for older operators |
| `apicurio.agent-inventory.google.url` | `https://agentregistry.googleapis.com` | Google Agent Registry base URL |
| `apicurio.agent-inventory.timeout.seconds` | `10` | Per Google request, including the body; Kubernetes uses `quarkus.kubernetes-client.request-timeout` |
| `apicurio.agent-inventory.items.max-size` | `1000` | Most items one listing may return |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| REST | [`GroupsResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/GroupsResourceImpl.java) (`syncAgentInventory`) | Authorization, audit, dispatch |
| Extension point | [`AgentInventorySyncHandler`](../app/src/main/java/io/apicurio/registry/extensions/AgentInventorySyncHandler.java) | Core-to-agents boundary |
| Reconciliation | [`AgentInventorySyncService`](../app/src/main/java/io/apicurio/registry/agents/inventory/AgentInventorySyncService.java) | Statuses, missing detection, tool import |
| Ownership | [`AgentInventoryLabels`](../app/src/main/java/io/apicurio/registry/agents/inventory/AgentInventoryLabels.java) | Reserved labels |
| Sources | [`InventorySource`](../app/src/main/java/io/apicurio/registry/agents/inventory/InventorySource.java), [`KubernetesInventory`](../app/src/main/java/io/apicurio/registry/agents/inventory/KubernetesInventory.java), [`KubernetesMcpServerSource`](../app/src/main/java/io/apicurio/registry/agents/inventory/KubernetesMcpServerSource.java), [`KubernetesAgentCardSource`](../app/src/main/java/io/apicurio/registry/agents/inventory/KubernetesAgentCardSource.java), [`GoogleAgentRegistrySource`](../app/src/main/java/io/apicurio/registry/agents/inventory/GoogleAgentRegistrySource.java) | Listing and mapping |
| Mapping | [`AgentCards`](../app/src/main/java/io/apicurio/registry/agents/inventory/AgentCards.java), [`InventoryItem`](../app/src/main/java/io/apicurio/registry/agents/inventory/InventoryItem.java) | A2A normalization; item model |
| Errors, headers | [`InventorySourceException`](../app/src/main/java/io/apicurio/registry/agents/inventory/InventorySourceException.java), [`OutboundHeaders`](../app/src/main/java/io/apicurio/registry/agents/OutboundHeaders.java) | Fixed messages; header validation |
| Configuration | [`AgentInventoryConfig`](../app/src/main/java/io/apicurio/registry/agents/inventory/AgentInventoryConfig.java) | Properties above |

## Limitations and open work

1. Experimental; the endpoint may change incompatibly. Graduation is tracked in
   [#10294](https://github.com/Apicurio/apicurio-registry/issues/10294).
2. Only the listing is atomic. Items are written one by one, so a sync that fails part-way (for
   example when one create conflicts with a concurrent write) leaves the earlier items written; the next sync reconciles them ([#11351](https://github.com/Apicurio/apicurio-registry/issues/11351)).
3. Content is compared as exact strings with the latest version, so a source that re-serializes an
   unchanged item differently produces a new version ([#11351](https://github.com/Apicurio/apicurio-registry/issues/11351)).
4. No built-in schedule, metrics or sync-specific logs; observability is the audit log and the
   per-item report ([ADR-0021](0021-observability.md) covers the general signals).
5. The operator does not yet configure the sync or its RBAC
   ([#10295](https://github.com/Apicurio/apicurio-registry/issues/10295)). Runtime identity of the
   synced agents is not recorded ([#11332](https://github.com/Apicurio/apicurio-registry/issues/11332)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Background scheduler with a system identity | Not selected: bypasses per-caller authorization and attribution (see above) |
| Push from the operators (webhooks) | Not selected: needs changes in every source; a pull works with sources as they are |
| Bidirectional sync with Kagenti `AgentCard`s (#8407 PoC) | Not selected: the source stays the source of truth; the registry only reads |
| Delete artifacts no longer listed | Not selected: deprecating keeps history and references valid, and a transient gap is reversible |
| Ownership by artifact ID prefix alone | Not selected: would overwrite hand-made artifacts with the same ID; labels plus ID make it `NOT_MANAGED` |

## Consequences

- **Benefits:** one governed inventory across clusters and platforms; every write is authorized,
  rule-checked and audited as the caller; nothing is deleted or overwritten that the sync did not
  create.
- **Costs:** callers must schedule syncs; one adapter per source to keep up with fast-moving operator
  and vendor APIs; a partial failure can leave a sync half applied until the next run.

## Example and verification

- **Example:** none. [`examples/agent-registry`](../examples/agent-registry/) does not exercise
  inventory sync.
- **User documentation:**
  [`assembly-agent-inventory-sync.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-agent-inventory-sync.adoc).

| Concern | Tests to read |
|---|---|
| Reconciliation, statuses, sources, bounds | `AgentInventorySyncTest` (app) |
| KafkaSQL | `AgentInventoryKafkaSqlTest` (app) |
| Read-only storage | `AgentInventoryGitOpsTest` (app) |
| Feature gate | `AgentInventoryFeatureGateTest` (app) |
| Authorization (403 and allowed) | `AgentInventoryAuthTest` (app) |
| Mapping and normalization | `InventoryMappingTest` (app) |
| Outbound headers | `OutboundHeadersTest` (app) |

```bash
./mvnw test -pl app -Dtest='AgentInventory*Test,InventoryMappingTest,OutboundHeadersTest'
```

## History

| Milestone | Result |
|---|---|
| [#11336](https://github.com/Apicurio/apicurio-registry/pull/11336) | Inventory sync with the three sources |
