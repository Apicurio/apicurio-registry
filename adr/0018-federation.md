# ADR-0018: Federation with Peer Registries

- **Status:** Proposed. The peer model, its storage and the peer management API are implemented; federated
  agent search is in review ([#10492](https://github.com/Apicurio/apicurio-registry/pull/10492)) and the
  rest of the design (credentials, UI, operator) is still settling. This ADR records the decisions taken so
  far and will be accepted when federated search is merged.
- **Decision date:** 2026-11-17 (decisions from 2026-09-21, see Tracking).
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#8424](https://github.com/Apicurio/apicurio-registry/issues/8424) (epic),
  [design discussion #10012](https://github.com/Apicurio/apicurio-registry/discussions/10012) and its
  [decisions](https://github.com/Apicurio/apicurio-registry/discussions/10012#discussioncomment-18537660),
  [#8573](https://github.com/Apicurio/apicurio-registry/issues/8573),
  [#8574](https://github.com/Apicurio/apicurio-registry/issues/8574),
  [#8575](https://github.com/Apicurio/apicurio-registry/issues/8575),
  [#8576](https://github.com/Apicurio/apicurio-registry/issues/8576),
  [#8578](https://github.com/Apicurio/apicurio-registry/issues/8578),
  [#8579](https://github.com/Apicurio/apicurio-registry/issues/8579),
  [#8580](https://github.com/Apicurio/apicurio-registry/issues/8580).
- **Scope:** How one registry knows about other registries (peers) and queries them for AI agents: the peer
  model and where it is stored, the outbound address policy, and the contract of federated search
  (visibility, deadlines, partial results, identity). Not covered: ARD federation and third-party
  catalogs ([ADR-0004](0004-ai-catalog-ard-convergence.md)), and replicating data between registries
  (export and import, [ADR-0012](0012-import-export-and-migration.md)).

## Context

Organizations run several registries (per region, per business unit, per environment), and an agent
looking for another agent should not need to know which one holds it. A2A discovery
([ADR-0014](0014-agent-registry.md)) works per registry. Federation adds a way to ask peers too, without
copying their data: each registry stays the owner of its artifacts and of who may see them. Asking another
server on a caller's behalf raises the usual risks: leaking what a caller may not see, server-side request
forgery through peer URLs, one slow peer slowing every search, and loops between registries.

## Decision

1. **Peers are admin-managed configuration, stored with the data.** A peer is `peerId` (lowercase
   `[a-z0-9._-]`, `local` reserved), `url`, optional `name` and `description`, `enabled`, and an optional
   `credentialSecretRef`. Peers are stored on every storage variant: a `peers` table on SQL, journal messages
   on KafkaSQL, and a declarative `peers` list in the registry file on GitOps and KubernetesOps (replaced as
   a whole on each load; the admin API answers 409 there). Peers are not exported.
2. **Peer management is an experimental admin API.** `/apis/registry/v3/admin/peers` (Admin role), only when
   `apicurio.federation.enabled`; 409 otherwise.
3. **Credentials are references, never values.** `credentialSecretRef` names a key in a credentials
   directory (a mounted Secret on Kubernetes), read at call time. Secret contents never enter peer data, the
   journal, responses or logs; a caller's token is never forwarded.
4. **Peer URLs follow an outbound address policy.** `PeerAddressPolicy` requires HTTPS unless
   `apicurio.federation.peer.insecure-http.enabled`, refuses user information and fragments, and allows
   private and cluster addresses (a sibling registry over service DNS is the normal case). Link-local,
   unspecified, multicast, reserved and cloud metadata addresses are always refused; loopback only with
   `apicurio.federation.peer.loopback.enabled`. The URL is checked when a peer is written or loaded; the
   resolved address must be checked again when connecting, on the address actually used. Redirects are not
   followed. Structural validation (`PeerValidator`) is separate, so replaying the KafkaSQL journal never
   fails because configuration changed.
5. **Federated search (in review):** `GET /apis/registry/v3/search/federated` queries the local registry and
   every enabled peer's agent search, with the existing agent filters, Read role. Its contract:
   - **Visibility:** local results keep the caller's visibility rules; remote results are **public only**,
     through an explicit public-only mode that the peer must confirm in its answer. A peer that does not
     confirm it (an older server that ignores the parameter) is reported as unsupported and its entries are
     discarded.
   - **Single hop:** a peer answers from its own data only.
   - **Bounded:** coordinator-owned deadlines (5 s global, 3 s per peer), at most 16 enabled peers, a global
     bound on concurrent peer calls, and bounded response size; results per source (`perSourceLimit`) with
     explicit truncation, no merged paging.
   - **Partial results with typed outcomes:** each source reports its outcome (deadline, timeout, unsupported,
     authorization failure, transport or server error, circuit open, invalid response, capacity exceeded);
     the response is 200 when the local search succeeds. One circuit breaker per peer.
   - **No cross-source deduplication:** results are identified by `(source, groupId, artifactId)`; the same
     coordinates in two registries are two results.
6. **Not in this design:** ARD federation, publisher verification, referrals, MCP federation, third-party
   catalogs.

### Why public-only remote results

Federating a caller's identity across registries needs a shared identity model and trust between servers
that do not exist yet. Public-only results are safe to merge whoever asks; anything else would expose what a
peer's service account can see.

### Why the peer must confirm public-only mode

An older or misconfigured peer that ignores an unknown parameter would return everything it sees. Only an
answer that says it applied the mode is trusted.

## Model

| Storage | Peers come from | Admin API |
|---|---|---|
| SQL (H2, PostgreSQL, MySQL, SQL Server) | `peers` table | Read and write |
| KafkaSQL | Journal (`CreatePeer1Message`...) | Read and write |
| GitOps, KubernetesOps | `peers` in the registry file, replaced on every load | Read only (409 on write) |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.federation.enabled` | `false` | Peer management (and federated search); experimental |
| `apicurio.federation.peer.insecure-http.enabled` | `false` | Allow `http://` peer URLs, server-wide |
| `apicurio.federation.peer.loopback.enabled` | `false` | Allow loopback peer URLs, server-wide (development) |

The federated search limits (deadlines, peer count, concurrency) are added with
[#10492](https://github.com/Apicurio/apicurio-registry/pull/10492).

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Configuration and policy | [`federation`](../app/src/main/java/io/apicurio/registry/federation/) | Feature flags, `PeerAddressPolicy` |
| Validation | [`PeerValidator`](../app/src/main/java/io/apicurio/registry/storage/PeerValidator.java) | Structural rules, journal-safe |
| Storage | `SqlPeerRepository`, KafkaSQL peer messages, the polling model's `Peer` | Persistence per variant |
| API | `AdminResourceImpl` (`/admin/peers`) | Peer management |
| Address checks | [`OutboundAddressPolicy`](../app/src/main/java/io/apicurio/registry/http/OutboundAddressPolicy.java) | Shared with MCP import ([ADR-0014](0014-agent-registry.md)) |

## Limitations and open work

1. Federated search is not merged yet ([#8575](https://github.com/Apicurio/apicurio-registry/issues/8575),
   [#10492](https://github.com/Apicurio/apicurio-registry/pull/10492)); credentialed peer queries come later.
2. No UI ([#8576](https://github.com/Apicurio/apicurio-registry/issues/8576)), no operator support
   ([#8578](https://github.com/Apicurio/apicurio-registry/issues/8578)), no user documentation yet
   ([#8580](https://github.com/Apicurio/apicurio-registry/issues/8580)).
3. HTTP and loopback opt-ins are server-wide, not per peer.
4. Remote results are public only; there is no federated identity.
5. Peers are not part of export and import.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| ARD federation (option a/b in #10012) | Deferred: a standard surface, but publisher verification and third-party trust are not designed yet |
| Forward the caller's token to peers | Rejected: leaks credentials across trust domains |
| Credentialed peer queries returning everything the service account sees | Rejected: callers would see more than they may |
| Deduplicate by host identifier or URN | Deferred: a matching hostname proves neither ownership nor identity |
| Replicate peers' data locally | Not selected: copies and staleness; each registry stays the owner |

## Consequences

- **Benefits:** one search across registries without copying data or ownership; safe defaults (public only,
  HTTPS, bounded); peers configurable declaratively on every storage.
- **Costs:** search latency bounded by the slowest peer within the deadline; partial results that clients
  must handle; a peer list to maintain on each registry.

## Example and verification

- **Example:** none yet. The multi-instance demonstration is part of
  [#8579](https://github.com/Apicurio/apicurio-registry/issues/8579) (`FederatedSearchMultiInstanceIT` in
  [#10492](https://github.com/Apicurio/apicurio-registry/pull/10492)); a runnable example will follow it.
- **User documentation:** declarative peers in
  [`assembly-configuring-gitops-storage.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-gitops-storage.adoc);
  the peer API in the REST API reference; the federation guide is
  [#8580](https://github.com/Apicurio/apicurio-registry/issues/8580).

| Concern | Tests to read |
|---|---|
| Peer validation and address policy | `PeerValidatorTest`, `PeerAddressPolicyTest` (app) |
| Peer API, authorization, feature gate | `PeersResourceTest`, `KafkaSqlPeersResourceTest`, `PeersAuthTest`, `PeersFeatureGateTest` (app) |
| Declarative peers | `GitOpsSmokeTest`, `KubernetesOpsSmokeTest` (app) |

## History

| Milestone | Result |
|---|---|
| 2026-09-21 | Design decisions on #10012 |
| [#10234](https://github.com/Apicurio/apicurio-registry/pull/10234), [#10274](https://github.com/Apicurio/apicurio-registry/pull/10274) | Peer storage on every variant; peer management API |
| This ADR | First written record, Proposed until federated search is merged |
