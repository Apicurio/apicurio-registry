# ADR-NNNN: <Feature or decision title>

<!--
How to use this template
- Copy to adr/NNNN-short-kebab-title.md, using the next free four-digit number.
- Delete these comments and any optional section you do not need. Keep the required ones.
- Write about the current implementation; put superseded decisions under History, not inline.
- Link source with repository-relative paths (../app/src/main/java/...), not line numbers.
- State limits and non-guarantees explicitly. Do not claim numbers (latency, sizes) without a
  benchmark or test that backs them.
- The reference ADR is 0005-per-resource-authorization.md.

Required: header, Context, Decision, Consequences, Alternatives, Example and verification.
Optional: Reading this document, model/behaviour sections, Configuration, Source map,
Limitations and open work, History.
-->

- **Status:** Proposed | Accepted | Superseded by [ADR-NNNN](NNNN-title.md) | Deprecated.
- **Decision date:** YYYY-MM-DD.
- **Author:** <name>.
- **Tracking:** [#NNNN](https://github.com/Apicurio/apicurio-registry/issues/NNNN).
- **Scope:** <One or two sentences: what this covers, and what it explicitly does not.>

## Reading this document

<!-- Optional, for long ADRs: where to start for each audience (operator, API user, contributor). -->

- Start with [the decision](#decision).
- For deployment, see [configuration](#configuration) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-<topic>.adoc).
- For a running system, see the [example](../examples/<example>/).
- For code changes, see the [source map](#source-map) and [verification](#example-and-verification).

## Context

<!-- The problem and the forces at play. What existed before, why it was not enough, and which
constraints any solution must respect (storage variants, auth, compatibility, upgrades). -->

## Decision

<!-- Numbered, testable statements. Each should be something a reviewer can check in code. -->

1. <Decision>.
2. <Decision>.

### Why <the non-obvious part>

<!-- Optional: rationale for any choice a reader would otherwise question. -->

## <Model / behaviour>

<!-- Optional, one or more sections: concepts, request flow, data model, ordering of checks.
Prefer tables for mappings and a worked example over prose. -->

### Behaviour across storage variants

<!-- Required for storage-touching features: SQL (H2/PostgreSQL/MySQL/SQL Server), KafkaSQL,
GitOps, KubernetesOps. State any variant that behaves differently or is unsupported. -->

| Variant | Behaviour |
|---|---|
| SQL | |
| KafkaSQL | |
| GitOps | |
| KubernetesOps | |

## Configuration

<!-- Optional. Defaults must match @ConfigProperty(defaultValue=) and @Info. -->

| Property | Default | Meaning |
|---|---|---|
| `apicurio.<feature>.enabled` | `false` | |

## Source map

<!-- Optional. -->

| Layer | Source | Responsibility |
|---|---|---|
| | [`Class`](../app/src/main/java/io/apicurio/registry/...) | |

## Limitations and open work

<!-- Optional but recommended: what this design does not guarantee, with tracking issues. -->

1. <Limitation> ([#NNNN](https://github.com/Apicurio/apicurio-registry/issues/NNNN)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| <Option> | <Why not chosen, or when it would be> |

## Consequences

<!-- Benefits and costs of the decision, including maintenance burden. -->

- **Benefits:** <...>
- **Costs:** <...>

## Example and verification

- **Example:** [`examples/<example>`](../examples/<example>/): `docker compose up -d && ./demo.sh`
  checks <what it demonstrates>.
- **User documentation:** [`assembly-<topic>.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-<topic>.adoc).

| Concern | Tests to read |
|---|---|
| <Concern> | `<TestClass>` |

```bash
./mvnw test -pl <module> -Dtest=<TestClass>
```

## History

<!-- Optional. -->

| Milestone | Result |
|---|---|
| [#NNNN](https://github.com/Apicurio/apicurio-registry/pull/NNNN) | <What changed> |
