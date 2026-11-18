# ADR-0020: Multitenancy in Registry 3.x

- **Status:** Accepted (records the current position). The in-instance tenant model is deferred to
  [#10667](https://github.com/Apicurio/apicurio-registry/issues/10667).
- **Decision date:** 2026-11-18.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#10667](https://github.com/Apicurio/apicurio-registry/issues/10667) (tenant model
  above groups), [#7536](https://github.com/Apicurio/apicurio-registry/issues/7536) (operator
  multi-instance provisioning), [enterprise authorization epic #8212](https://github.com/Apicurio/apicurio-registry/issues/8212).
- **Scope:** What "multitenancy" means for Registry 3.x today: which isolation models are supported,
  which are rejected, and which are deferred. Not covered: the design of a future in-instance tenant
  (that is #10667's ADR).

## Context

Registry 2.x had built-in multitenancy: a tenant ID in the URL path or a header, a `tenantId` column
in every table, a tenant manager service, and per-tenant limits. Registry 3.x removed all of it.
Nothing tenant-shaped remains in the application.

Users still need to serve several teams or customers. Two models exist today:

- **One instance per tenant**, provisioned by the operator: each tenant gets its own
  `ApicurioRegistry3` CR, so its own pods, storage, identity provider and configuration.
- **One shared instance**, where each tenant owns groups and authorization keeps tenants apart:
  owner-only authorization limits writes, and per-resource authorization
  ([ADR-0005](0005-per-resource-authorization.md)) limits reads, including in search.

The first isolates everything but costs a stack per tenant; a user with 100+ customers reported that
this does not scale (#10667). The second is cheap but has no per-tenant configuration, limits or
storage.

## Decision

1. **Registry 2.x multitenancy does not return.** No tenant ID in URLs or headers, no tenant column
   in storage, no tenant manager. Storage partitioning per tenant inside one instance is rejected:
   it touches every query in four storage variants, and the operator already gives full isolation.
2. **Strong isolation is one instance per tenant, through the operator.** This is the supported and
   documented model ([implementing multitenancy](../docs/modules/ROOT/pages/getting-started/assembly-implementing-multitenancy.adoc)):
   a namespace per tenant with one cluster-wide operator is recommended; a namespaced operator per
   tenant is possible. Storage, Kafka topics, identity provider and resources are per tenant.
3. **Lightweight isolation is groups plus authorization, in one instance.** A tenant is a set of
   groups. Owner-only authorization keeps other tenants from changing them; per-resource
   authorization keeps them from reading them, and filters search, counts and pagination. Content
   rules can differ per tenant, because group and artifact rules take precedence over global ones;
   instance configuration, limits, storage and the identity provider are shared.
4. **The in-instance tenant is deferred to #10667.** A first-class tenant (a named owner of groups,
   with its own configuration and limits) needs the tenant's definition, the isolation boundary in
   search, the per-tenant configuration surface, and a storage change across all four variants. It
   will be a separate ADR, reviewed by a second maintainer before it is accepted. The resource-scope
   contract (`scope-api`, [ADR-0005](0005-per-resource-authorization.md)) is the intended foundation,
   so that one grants source can serve the registry and a Kafka proxy.

## Model

| | One instance per tenant | Groups and authorization |
|---|---|---|
| Data | Separate storage | Shared storage, separate groups |
| Reads by other tenants | Impossible | Denied by per-resource rules (not by owner-only alone) |
| Writes by other tenants | Impossible | Denied by owner-only or per-resource rules |
| Content rules (validity, compatibility) | Per tenant | Per group or artifact, over global defaults |
| Instance configuration, limits | Per tenant | Global |
| Identity provider | Per tenant | Shared |
| Failure, upgrade, scaling | Per tenant | Shared |
| Cost | A stack per tenant (about 600m CPU and 768Mi memory minimum) | One stack |
| Provisioning | A CR, a database or topics | A group and access rules |

## Limitations and open work

1. Shared-instance tenants have no per-tenant instance configuration, quotas or metering (#10667).
2. With KafkaSQL on a shared Kafka cluster, every instance needs its own journal and snapshots
   topics; the defaults are the same for all instances.
3. Moving a tenant between the two models is an export and import of its groups
   ([ADR-0012](0012-import-export-and-migration.md)); there is no dedicated tool.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Bring back 2.x multitenancy | Rejected: a tenant column in every table and every query, for isolation the operator already gives |
| A database schema per tenant in one instance | Rejected: connection and migration management per tenant inside the app, weaker than separate instances |
| Only one instance per tenant | Not enough on its own: too costly for many small tenants, hence the group model |
| Define the in-instance tenant now | Deferred to #10667: it depends on the authorization evolution under #8212 |

## Consequences

- **Benefits:** no tenant code in the application; the strong model reuses the operator, the light
  model reuses authorization.
- **Costs:** users choose between cost and isolation; per-tenant configuration needs a separate
  instance until #10667.

## Example and verification

- **Instance per tenant:** [`examples/operator`](../examples/operator/) deploys independent
  `ApicurioRegistry3` CRs in separate namespaces; the operator's `MultiNamespaceSmokeITTest`,
  `MultitenancyPatternBITTest` and `RestrictedNamespaceITTest` cover the patterns.
- **Groups and authorization:** [`examples/per-resource-authorization`](../examples/per-resource-authorization/)
  shows access rules that decide, per user and group, what can be read and written, and search
  results filtered by them; a tenant boundary is rules that grant each tenant only its own groups.
- **User documentation:** [implementing multitenancy](../docs/modules/ROOT/pages/getting-started/assembly-implementing-multitenancy.adoc),
  [per-resource authorization](../docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc).

## History

| Milestone | Result |
|---|---|
| 2.x | Built-in multitenancy (#1145, #1282): tenant ID, tenant manager |
| 3.0 | Removed; one instance per tenant |
| 3.x | Operator multi-namespace patterns (#7536); per-resource authorization (ADR-0005) |
| This ADR | Position recorded; in-instance tenant deferred to #10667 |
