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
- Cycle-safe traversal: a coordinate is marked visited the moment it is queued, not after it
  finishes loading, so a cycle stops the walk from loading the same branch twice instead of
  recursing forever ([#9527][9527]).

### 2. Correctness

The result must not be silently wrong, regardless of whether the user asked for strict checking.
Always on — this is a bug fix, not a policy choice, because the alternative is a registry that
answers a question incorrectly without saying so.

- Resolving a reference never silently drops it because of a storage error. A missing
  artifact/version is recorded as "not found" (expected: it may not exist yet, or may have been
  deleted); every other failure propagates out of the walk instead of being logged and skipped
  ([#9626][9626], not yet fixed — see below).
- `NO_CIRCULAR_REFERENCES` fails the whole check when the walk underneath it fails, instead of
  reporting "no cycle" for a path it could not actually examine
  ([#9095][9095], [`IntegrityRuleExecutor.findCycleInReferenceGraph`][findcycle]).
- The reference-graph endpoint (`GET .../references/graph`) detects a cycle per *version*, not per
  artifact: a cross-version chain through the same artifact (`A@2 -> B@1 -> A@1`) is a valid chain,
  not a cycle, because `A@1` has no edge back to `A@2` ([#9069][9069]).
- A reference name that resolves to two different targets elsewhere in the same graph is detected
  and warned about ([#9000][9000], `ResolvedReferences.asNameMap()`). Today this is detection only
  — the warning explains what is ambiguous, but resolution still picks one target the same way it
  always has. Making the ambiguity itself a hard failure, or resolving it correctly per branch, is
  the harder follow-up tracked as [#10761][10761]; this tier only requires the *detection* half to
  be unconditional, which it already is.
- A duplicate reference name within one version is structurally impossible regardless of
  `INTEGRITY` configuration: `content_references` has `PRIMARY KEY (contentId, name)`, enforced by
  every SQL dialect's DDL, and `SqlContentRepository.insertReferencesRaw` turns the resulting
  constraint violation into a `ConflictException` unconditionally. The opt-in `NO_DUPLICATES`
  level below checks the same thing earlier, for a cleaner error — it is not what prevents the
  duplicate.

### 3. Policy

Whether to reject a write or a read over something merely surprising, not wrong. Opt-in, through
the `INTEGRITY` rule's `IntegrityLevel`s, exactly as configured today:

- `REFS_EXIST`: every declared reference resolves to an existing artifact version, and (via
  `validateReferenceTargetTypes`) to a type this artifact type is allowed to reference. A write
  with a reference to something that does not exist yet is not *wrong* — it may be filled in later,
  or the registry may simply not enforce strict ordering — so this is a choice, not a correctness
  requirement.
- `ALL_REFS_MAPPED`: every reference the content itself uses has a corresponding registry mapping.
  Not yet implemented (needs artifact-type-specific extraction of the content's own reference list).
- `NO_DUPLICATES`: rejects a duplicate reference name at write time, with
  `RuleViolationException` instead of the DB's raw constraint violation. Redundant with the
  structural guarantee above for *this* version's own references, but still the only thing that
  gives a clean error instead of a storage-layer one.
- `NO_CIRCULAR_REFERENCES`: rejects a write that would introduce a cycle. A cycle is not
  structurally wrong the way a crash or data corruption would be — some graphs legitimately have
  cycles that the registry can still store and traverse safely (tier 1 guarantees that) — so
  whether to reject one at write time is a policy decision, same as the others in this tier.

## Migration: moving a check to a stricter tier

Moving a check from policy to safety or correctness changes what used to be accepted into what now
fails. That is a behavior change for every deployment that was relying on the old, looser
behavior — intentionally or not — so it needs a migration story, at minimum:

1. Ship a dry-run / report-only mode at least one release ahead of the default changing, so
   operators can see what existing content would newly fail before it does.
2. Document the change in the release notes as a behavior change, not just a bug fix, even though
   from this framework's point of view it is a bug fix.

[#9000][9000]'s detect-and-warn step is this pattern in miniature: the warning ships now, the
tier-2 guarantee (today: detection only, not resolution) is already unconditional, and a future
PR that turns detection into enforcement gets its own release-ahead warning period if it ever
changes what is accepted, via the richer resolution [#10761][10761] is tracked against.

## Open question 1 (#10635), answered

*When a referenced version does not exist — for example it was deleted while no integrity rule
protected it — should resolution fail or skip it?*

Tier 2 (correctness) answers this: skip it, but only because a missing version is an *expected*
outcome of resolution (the version may not exist yet, or may have been deleted), not because
errors in general are tolerated. Any other failure while trying to resolve that same reference —
a storage outage, a corrupt row, anything that is not simply "this version is not there" — must
propagate, per tier 2. `ReferenceGraphWalker`'s loader contract already encodes exactly this split:
`ArtifactNotFoundException`/`VersionNotFoundException` are caught by the loader and recorded as
"missing"; everything else is left to propagate out of `loadBatch`, out of `walk`, and out of
whatever rule or endpoint triggered the walk (see `IntegrityRuleExecutor.loadBatch` for one
example, and `ReferenceGraphWalker`'s own class Javadoc for the walker's side of the contract).

This still leaves dangling references silently unreported day to day, by design — tier 2 only
requires that *resolving* a reference that happens to be missing not be confused with resolution
*failing*. Making dangling references visible (as opposed to silently tolerated) is a separate,
opt-in concern, tracked as [#10048][10048] (an admin-facing scan/report), not a correctness
requirement on every resolution.

## What this issue does not do

- No behavior changes. Every check above already runs exactly as described; this is a naming and
  classification exercise, not a PR that moves anything between tiers.
- [#9626][9626] (walker 1/2's own `recursivelyResolveReferences`/`resolveReferencesWithContext`
  entry points still swallowing non-missing errors via their thin-wrapper loaders) is a known,
  still-open gap in tier 2, already filed and assigned — not newly discovered by this
  classification, and not fixed here.
- Deciding whether any *specific* check should actually move tiers. The tiers above describe where
  each check sits today; moving one is its own change, with its own migration plan per the section
  above.

[9000]: https://github.com/Apicurio/apicurio-registry/issues/9000
[9069]: https://github.com/Apicurio/apicurio-registry/issues/9069
[9095]: https://github.com/Apicurio/apicurio-registry/issues/9095
[9527]: https://github.com/Apicurio/apicurio-registry/issues/9527
[9626]: https://github.com/Apicurio/apicurio-registry/issues/9626
[10048]: https://github.com/Apicurio/apicurio-registry/issues/10048
[10635]: https://github.com/Apicurio/apicurio-registry/issues/10635
[10761]: https://github.com/Apicurio/apicurio-registry/issues/10761
[walker]: ../../../references/ReferenceGraphWalker.java
[findcycle]: IntegrityRuleExecutor.java
