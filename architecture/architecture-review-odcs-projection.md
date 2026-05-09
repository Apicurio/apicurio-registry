# ODCS Projection Model — Architecture Review

## Current Design

ODCS contracts are submitted as YAML, stored as `ODCS_CONTRACT` artifacts, and their contents are **projected** onto referenced schema artifacts:

```
ODCS YAML → Parser → Projection Engine → Schema Artifact
                                            ├── contract.* labels (metadata)
                                            ├── contract_rules rows (CEL quality rules)
                                            └── field-tag.*|* version labels (PII/tags)
```

The ODCS YAML is also stored as a separate artifact for versioning and export.

## Fundamental Assumption

**The projection model assumes 1:1 between contracts and schema artifacts.**

Each projection strips all `contract.*` labels and `odcs:`-prefixed rules from the schema artifact before writing the new contract's data. This means the last contract projected wins.

### What Breaks

If Contract A (owned by team-orders) and Contract B (owned by team-payments) both reference Schema X:

1. Team-orders submits Contract A → Schema X gets `contract.owner=orders-team`, `odcs:positive-amount` rule
2. Team-payments submits Contract B → Schema X gets `contract.owner=payments-team`, Contract A's rules and labels are silently overwritten

There is no error, no warning, no merge — Contract A's governance data is destroyed.

### Impact Assessment

- **Common case (1 contract per schema):** Works correctly. This is the expected usage pattern for most organizations.
- **Multi-team case (N contracts per schema):** Data corruption. The last writer wins.
- **Multi-schema in one contract:** Works correctly (all schemas projected independently).

## Design Alternatives

### Option 1: Contract-First (no projection)

Store the ODCS artifact as the sole source of truth. When rules need to be evaluated, read the contract dynamically and apply rules at runtime.

```
Submit:   ODCS YAML → store as artifact (done)
Enforce:  Schema registration → find contracts referencing this schema → evaluate rules
```

**Pros:**
- No label mutation, no race conditions
- Multiple contracts per schema work naturally
- Single source of truth

**Cons:**
- Requires rule engine changes to read from contracts, not artifact labels
- Higher latency at enforcement time (contract lookup on every schema operation)
- Breaks the existing Phase 1 contract metadata model (labels-based)

### Option 2: Namespaced Projection

Prefix projected labels with the contract ID to prevent collisions:

```
contract.{contractId}.owner = orders-team
contract.{contractId}.status = STABLE
odcs:{contractId}:positive-amount (rule name)
field-tag.{field}|{contractId}:PII (version label)
```

**Pros:**
- Multiple contracts per schema coexist
- Each contract's projection is independent
- No data loss on concurrent projections

**Cons:**
- Label proliferation (N contracts × M labels per contract)
- Querying "who owns this schema?" requires scanning all `contract.*.owner` labels
- Export needs to know which contract ID to reconstruct
- More complex label management

### Option 3: Thin Wrapper (no ODCS artifact)

ODCS is just an input format that translates to the existing Phase 1 label/rule API. No separate `ODCS_CONTRACT` artifact type.

```
Submit:   ODCS YAML → parse → call PUT /contract/metadata + PUT /contract/ruleset
Export:   GET /contract/metadata + GET /contract/ruleset → reconstruct ODCS YAML
```

**Pros:**
- Simplest architecture, fewest moving parts
- No dual storage (labels are the only representation)
- Works with existing Phase 1 endpoints

**Cons:**
- ODCS YAML not versioned as an artifact (can't retrieve original)
- Round-trip lossy (ODCS fields not in the Phase 1 model are lost)
- No `@JsonAnySetter` preservation of unknown fields

### Option 4: Event-Driven Projection (async)

Submit stores the ODCS artifact, then fires a CDI event. An async observer performs the projection. Projection failures don't block contract submission.

```
Submit:   ODCS YAML → store artifact → fire ContractSubmitted event → return
Observer: ContractSubmitted → project labels/rules/tags (async)
```

**Pros:**
- Better for KafkaSQL (event-driven model fits naturally)
- Submission is fast (no blocking projection I/O)
- Projection can be retried on failure

**Cons:**
- Eventually consistent (labels may not be present immediately after submit)
- Same 1:1 limitation as current design unless combined with Option 2

## Recommendation

**For the current PR:** Merge as-is with the 1:1 limitation documented. The common case works correctly, and the architecture is evolvable.

**For a follow-up:** Implement Option 2 (namespaced projection) when multi-contract-per-schema becomes a real requirement. The change is backward-compatible — existing contracts get their ID as the namespace prefix, and the projection engine checks for conflicts before overwriting.

**Long-term:** Option 1 (contract-first) is the cleanest architecture but requires significant rule engine refactoring. Consider it for a major version where breaking changes are acceptable.

## Other Design Observations

### Dual Storage (ODCS Artifact + Projected Labels)

The ODCS YAML exists as an artifact AND its contents are projected as labels/rules. These two representations can diverge if:
- Projection fails after artifact creation (partial state)
- Someone modifies schema artifact labels directly (bypassing the contract)
- The ODCS artifact is deleted but projected labels remain

**Mitigation:** The ODCS artifact is the source of truth. Projected labels can be re-derived by re-submitting the contract.

### Label-Based Tag Storage

Field tags as version labels (`field-tag.{path}|{tag}`) work for small schemas but have scaling concerns:
- Label keys have storage-dependent size limits
- No efficient index for "find all artifacts with PII tags"
- Long field paths in deeply nested schemas produce long label keys

**Mitigation:** Acceptable for the current feature scope. If tag queries become a performance concern, a dedicated tag index can be added later without changing the label storage.

### Synchronized Projection Lock

The per-artifact `synchronized` block in `OdcsProjectionEngine` prevents concurrent label corruption on a single JVM. In multi-node KafkaSQL deployments, concurrent projections to the same schema artifact from different nodes can still race.

**Mitigation:** The ODCS artifact is the source of truth. In the rare case of multi-node race, the last projection wins, and re-submitting the contract corrects the state. For strict consistency, use the SQL storage variant.
