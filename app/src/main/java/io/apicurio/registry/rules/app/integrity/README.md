# Reference and integrity check tiers

Every check that guards the reference graph — whether it lives in this package, in the shared
walker (`io.apicurio.registry.references`), or in a SQL `PRIMARY KEY` constraint — falls into one
of three tiers. The split was already real and inconsistently applied across these checks before
it had a name ([#9000][9000], [#9095][9095]); naming it here is so the next reference bug does not
have to re-ask "should this be an opt-in rule or always-on" from scratch, and resolves [#10635][10635]'s
open question 1 about dangling references, below.

## The three tiers

### 1. Safety

Prevents a crash or unbounded resource use. Always on, never configurable — there is no scenario
where accepting this risk is a reasonable trade-off, so there is nothing for an operator to decide.

- The reference-graph walker's depth limit
  (`apicurio.storage.references.max-depth`, [`ReferenceGraphWalker`][walker]): a graph deeper than
  the limit throws `ReferenceDepthExceededException` rather than recursing until the stack
  overflows. The property only tunes *how deep is too deep*, not *whether* the limit applies.
  This guarantee is specific to `ReferenceGraphWalker` — see the gaps listed under tier 2 below for
  the two older entry points that do not have it.
- Cycle-safe traversal: a coordinate is marked visited the moment it is queued, not after it
  finishes loading, so a cycle stops the walk from loading the same branch twice instead of
  recursing forever ([#9527][9527]). Again, specific to `ReferenceGraphWalker`; see the gaps below.

### 2. Correctness

The result must not be silently wrong, regardless of whether the user asked for strict checking.
Always on — this is a bug fix, not a policy choice, because the alternative is a registry that
answers a question incorrectly without saying so.

This tier is about what an *operation's result* promises, not about what state the registry is
allowed to be in. A dangling reference is a legitimate registry state (see "Open question 1"
below) — tier 2 does not forbid it. What it forbids is an operation whose result *depends on* a
referenced version's content returning something silently wrong when that reference turns out to
be missing:

| Depends on referenced content? | Examples | On a missing reference |
|---|---|---|
| No | the reference-graph walk and `GET .../references/graph` (represents it as a placeholder node), `PRESERVE`/`REWRITE` resolution modes, listing an artifact's own references | proceeds; the missing reference is reported where the caller can see it (`ResolvedReferences.missing()`), not hidden |
| Yes | `DEREFERENCE` (v3 and v2 `dereference=true`), ccompat `format=resolved`, serde-side reference resolution | must fail with a clear error naming the missing reference(s), not return partial or incorrect content ([#11059][11059], not yet fixed — a missing reference today can return an incomplete schema or a 500 instead) |

Not yet classified against this table (read from the code, not verified by a test run — flag if
you use one of these as load-bearing): canonical hashing at import (`SqlDataImporter` canonicalizes
using resolved references, so a reference that cannot be resolved changes the computed hash and
therefore deduplication), rule application during GitOps validation (`PollingDataValidator` passes
only the references it could resolve), and compatibility checks that take references into account.

What is already correct, or already known not to be:

- Resolving a reference never silently drops it because of a storage error. A missing
  artifact/version is recorded as "not found" (expected: it may not exist yet, or may have been
  deleted); every other failure propagates out of the walk instead of being logged and skipped
  ([#9626][9626], fix in review as [#10973][10973], not yet merged).
- `NO_CIRCULAR_REFERENCES` fails the whole check when the walk underneath it fails, instead of
  reporting "no cycle" for a path it could not actually examine
  ([#9095][9095], [`IntegrityRuleExecutor.findCycleInReferenceGraph`][findcycle]).
- The reference-graph endpoint (`GET .../references/graph`) detects a cycle per *version*, not per
  artifact: a cross-version chain through the same artifact (`A@2 -> B@1 -> A@1`) is a valid chain,
  not a cycle, because `A@1` has no edge back to `A@2` ([#9069][9069], fix open as [#10794][10794],
  not yet merged).
- A reference name that resolves to two different targets elsewhere in the same graph is detected
  and warned about ([#9000][9000], `ResolvedReferences.asNameMap()`). Today this is detection only
  — the warning explains what is ambiguous, but resolution still picks one target the same way it
  always has. Making the ambiguity itself a hard failure, or resolving it correctly per branch, is
  the harder follow-up tracked as [#10761][10761]; this tier only requires the *detection* half to
  be unconditional, which it already is **for `ReferenceGraphWalker`** — see the gap below for the
  two entry points where it is not.
- A duplicate reference name within one version is structurally impossible regardless of
  `INTEGRITY` configuration: `content_references` has `PRIMARY KEY (contentId, name)`, enforced by
  every SQL dialect's DDL, and `SqlContentRepository.insertReferencesRaw` turns the resulting
  constraint violation into a `ConflictException` unconditionally. The configurable `NO_DUPLICATES`
  level below checks the same thing earlier, for a cleaner error — it is not what prevents the
  duplicate.

**Known gaps — two entry points do not yet have the guarantees above.** The depth limit, the
cycle-safe traversal and the unconditional #9000 collision warning are all properties of
`ReferenceGraphWalker` specifically. Two older, not-yet-migrated resolution paths lack them:

- `SqlContentRepository.resolveReferencesRecursive` (called from `resolveReferencesRaw`): on
  exceeding the depth limit it logs a warning and silently truncates the result rather than
  throwing — a caller reading an incomplete resolved-references map has no way to tell it is
  incomplete. It also builds its resolved-references map keyed by name with no collision warning:
  the first reference processed with a given name wins silently, unlike
  `ResolvedReferences.asNameMap()`.
- `RegistryContentUtils.resolveReferencesWithContext`: has no depth limit at all. Its visited guard
  (`partialRecursivelyResolvedReferences`) is populated only after a branch's recursive call
  returns, not when it is queued — the same ordering bug `ReferenceGraphWalker` was written to
  avoid ([#9527][9527]) — so by reading the code it would recurse forever on a reference cycle (not
  reproduced with a real cycle; flagged here as a likely, not confirmed, hang). It also swallows
  every exception from resolving a reference as a logged error and continues
  (`catch (Exception ex) { log.error(...); }`), and silently overwrites `referencesRewrites` on a
  name collision instead of warning.

Both are pre-existing, not introduced by this classification, and not fixed here; tracked against
[#10635][10635] as unmigrated callers of the shared walker.

### 3. Policy

Whether to reject a write or a read over something merely surprising, not wrong. What makes a check
tier 3 rather than tier 2 is that it is **configurable at all**: a deployment may reasonably choose
either behavior, so the registry exposes a switch. Tiers 1 and 2 are never configurable — there is
no "off" position because there is no deployment for which the looser behavior is acceptable.

The *default* a tier-3 check ships with is a separate decision from its tier, with its own
rationale per check — being tier 3 does not imply "off by default". For example, ccompat's
subject-delete path already refuses to delete a schema version that other subjects still actively
reference, unconditionally, because the Confluent API contract requires it
(`AbstractResource.areAllSchemasDisabled`, fixed to require *all* referrers disabled, not merely
one — [#10598][10598]); that check has no switch and is not a candidate for tier 3 at all; it is
really tier 2 for that API, because the Confluent contract makes a wrong answer there a correctness
bug rather than a policy question. A check that is "opt-out because nobody should realistically turn
it off" belongs in tier 2 without a switch, not in tier 3 with a switch nobody should use.

Through the `INTEGRITY` rule's `IntegrityLevel`s — off (`NONE`) unless a group or artifact
explicitly configures the rule, exactly as configured today:

- `REFS_EXIST` (default: off): every declared reference resolves to an existing artifact version,
  and (via `validateReferenceTargetTypes`) to a type this artifact type is allowed to reference. A
  write with a reference to something that does not exist yet is not *wrong* — it may be filled in
  later, or the registry may simply not enforce strict ordering — so this is a choice, not a
  correctness requirement.
- `ALL_REFS_MAPPED` (default: off): every reference the content itself uses has a corresponding
  registry mapping, checked per artifact type by `ContentValidator.validateReferences` (implemented
  for Avro, JSON Schema, GraphQL, Kafka Connect and the ODCS contract type; a no-op for types
  without their own reference-extraction logic).
- `NO_DUPLICATES` (default: off): rejects a duplicate reference name at write time, with
  `RuleViolationException` instead of the DB's raw constraint violation. Redundant with the
  structural guarantee above for *this* version's own references, but still the only thing that
  gives a clean error instead of a storage-layer one.
- `NO_CIRCULAR_REFERENCES` (default: off): rejects a write that would introduce a cycle. A cycle is
  not structurally wrong the way a crash or data corruption would be — some graphs legitimately
  have cycles that the registry can still store and traverse safely (tier 1 guarantees that) — so
  whether to reject one at write time is a policy decision, same as the others in this tier.
- Delete-time referential integrity for the v3 API ([#10047][10047]; a prior report of the same gap,
  [#11029][11029], was closed as its duplicate): proposed, not yet implemented. Blocking a delete
  that would leave another artifact's reference dangling is the mirror image of `REFS_EXIST` at
  write time, and belongs in this tier for the same reason — forward and dangling references are
  both legitimate states the registry already tolerates, so preventing one at delete time is a
  policy choice. Its default (on or off) is still open, to be decided by Carles/Eric.

## Migration: changing what a check accepts by default

Any change to a check's *default* that rejects something previously accepted is a behavior change
for every deployment relying on the old, looser behavior — whether or not the change also moves the
check between tiers (e.g. turning [#10047][10047] on by default is this, without touching any tier).
At minimum:

1. Ship a dry-run / report-only mode at least one release ahead of the default changing, so
   operators can see what existing content would newly fail before it does.
2. Document the change in the release notes as a behavior change, not just a bug fix, even though
   from this framework's point of view it may be a correctness fix.

[#9000][9000]'s detect-and-warn step is this pattern in miniature: the warning ships now, the
tier-2 guarantee (today: detection only, not resolution) is already unconditional, and a future
PR that turns detection into enforcement gets its own release-ahead warning period if it ever
changes what is accepted, via the richer resolution [#10761][10761] is tracked against.

## Open question 1 (#10635), answered

*When a referenced version does not exist — for example it was deleted while no integrity rule
protected it — should resolution fail or skip it?*

Tier 2 (correctness) answers the resolution-level question: skip it, but only because a missing
version is an *expected* outcome of resolution (the version may not exist yet, or may have been
deleted), not because errors in general are tolerated. Any other failure while trying to resolve
that same reference — a storage outage, a corrupt row, anything that is not simply "this version is
not there" — must propagate, per tier 2. `ReferenceGraphWalker`'s loader contract already encodes
exactly this split: `ArtifactNotFoundException`/`VersionNotFoundException` are caught by the loader
and recorded as "missing"; everything else is left to propagate out of `loadBatch`, out of `walk`,
and out of whatever rule or endpoint triggered the walk (see `IntegrityRuleExecutor.loadBatch` for
one example, and `ReferenceGraphWalker`'s own class Javadoc for the walker's side of the contract).

What the *caller* does once a reference comes back "missing" is not a tier-2 decision — it is the
table in the Correctness section above: an operation whose result depends on the missing content
fails with a clear error naming it; one that does not proceeds and reports the gap where it can.

A version in the `DISABLED` state is not "missing" for this purpose — it exists and is resolvable,
just administratively hidden from some read paths. Treating `DISABLED` as equivalent to "does not
exist" has been a source of bugs before, in the opposite direction: ccompat's delete-time check
originally let a delete through as soon as *any* one referencing schema happened to be disabled,
instead of requiring all of them to be ([#10598][10598]). Any new "missing reference" handling that
also needs to special-case `DISABLED` targets should follow the same rule ccompat was fixed to use:
disabled is not absent.

This still leaves dangling references silently unreported day to day, by design — tier 2 only
requires that *resolving* a reference that happens to be missing not be confused with resolution
*failing*. Making dangling references visible (as opposed to silently tolerated) is a separate,
configurable concern, tracked as [#10048][10048] (an admin-facing scan/report), not a correctness
requirement on every resolution.

## What this issue does not do

- No behavior changes. Every check above already runs exactly as described; this is a naming and
  classification exercise, not a PR that moves anything between tiers.
- [#9626][9626] (walker 1/2's own `recursivelyResolveReferences`/`resolveReferencesWithContext`
  entry points still swallowing non-missing errors via their thin-wrapper loaders) is a known,
  still-open gap in tier 2, already filed and assigned (fix in review as [#10973][10973]) — not
  newly discovered by this classification, and not fixed here.
- Deciding whether any *specific* check should actually move tiers, or change its default. The
  tiers above describe where each check sits today; moving one, or changing a default, is its own
  change, with its own migration plan per the section above.

[9000]: https://github.com/Apicurio/apicurio-registry/issues/9000
[9069]: https://github.com/Apicurio/apicurio-registry/issues/9069
[9095]: https://github.com/Apicurio/apicurio-registry/issues/9095
[9527]: https://github.com/Apicurio/apicurio-registry/issues/9527
[9626]: https://github.com/Apicurio/apicurio-registry/issues/9626
[10047]: https://github.com/Apicurio/apicurio-registry/issues/10047
[10048]: https://github.com/Apicurio/apicurio-registry/issues/10048
[10598]: https://github.com/Apicurio/apicurio-registry/issues/10598
[10635]: https://github.com/Apicurio/apicurio-registry/issues/10635
[10761]: https://github.com/Apicurio/apicurio-registry/issues/10761
[10794]: https://github.com/Apicurio/apicurio-registry/issues/10794
[10973]: https://github.com/Apicurio/apicurio-registry/issues/10973
[11029]: https://github.com/Apicurio/apicurio-registry/issues/11029
[11059]: https://github.com/Apicurio/apicurio-registry/issues/11059
[walker]: ../../../references/ReferenceGraphWalker.java
[findcycle]: IntegrityRuleExecutor.java
