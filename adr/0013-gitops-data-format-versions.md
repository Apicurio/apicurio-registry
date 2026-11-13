# ADR-0013: GitOps Data Format Versions

- **Status:** Proposed.
- **Decision date:** 2026-11-12.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11101](https://github.com/Apicurio/apicurio-registry/issues/11101).
- **Scope:** How the file format read by the GitOps and KubernetesOps storage variants is versioned:
  what a version is, how long each version is read, how the code upgrades older files, and how users
  move a repository to a newer version. It doesn't cover the ZIP format of the default import and
  export.

## Reading this document

- Start with [the decision](#decision).
- For users with an existing repository, see [migrating a repository](#migrating-a-repository) and
  the [GitOps storage documentation](../docs/modules/ROOT/pages/getting-started/assembly-configuring-gitops-storage.adoc).
- For a contributor who changes the format, see [changing the format](#changing-the-format) and the
  [source map](#source-map).

## Context

GitOps storage reads registry data from metadata files in a Git repository, and KubernetesOps storage
reads the same files from ConfigMaps. Each file declares its kind and format version in `$type`, for
example `$type: artifact-v0`. Until 3.4 the only version was `v0`, and the feature was experimental,
so the format could change without notice.

From 3.4 the feature is supported. The repository belongs to the user, outlives any registry release,
and is often read by more than one registry version at once:

- During a rolling upgrade, old and new replicas read the same repository.
- One repository can serve several registry instances (`registryIds`), which are not always
  upgraded together.
- After a failed upgrade, users roll back to the previous registry version with the same repository.

So a format change can't be a flag day. Two things are needed: a rule for how long a version stays
readable, and code that reads an older version without spreading version checks through the loader.

The first change is from `v0` to `v1`. It is a rename only: `v1` has exactly the fields of `v0`. The
`v0` name signalled an unstable format, which no longer fits a supported feature.

## Decision

1. **The version is part of `$type`.** Every metadata file declares `$type: <kind>-v<N>`, where the
   kind is `registry`, `group`, `artifact` or `content`. The version applies to the whole file.
2. **Each version has one lifecycle state per release:**

   | State | Read | Written by the registry (export) | On a file in this version |
   |---|---|---|---|
   | Current | Yes | Yes | - |
   | Deprecated | Yes | No | One warning per load, with the number of files and an example path |
   | Removed | No | No | The load fails with an error that names the version and how to convert |

3. **At most two versions are readable in any release:** the current one, and at most one deprecated
   one.
4. **Versions are staggered.** A new version N+1 becomes current in release R, and N becomes deprecated
   in the same release. N is removed no earlier than two minor releases later (R+2), or in the next
   major release. Because only two versions are readable at a time, N+2 can't be introduced before N
   is removed.
5. **A newer, unknown version fails the load.** A file whose kind is known but whose version isn't (for
   example `artifact-v2` read by a registry that knows only `v0` and `v1`) fails the load, with an error
   saying it was written for a newer registry. The registry keeps serving its last good data. Silently
   skipping the file, as an unknown `$type` was handled before, would make its artifacts disappear from
   the older replicas.
6. **Within a version, changes are additive.** A new field must be optional, with a default that keeps
   the previous behaviour. Readers reject unknown fields, so a repository can use a new field only once
   every registry that reads it supports the field. The documentation names the release that added each
   field. Renaming or removing a field, changing its meaning or its default, or making it required needs
   a new version.
7. **Migration code is separate from loading.** Older files are upgraded on the parsed tree, one version
   at a time (`v0` to `v1`, then `v1` to `v2`), before they are bound to the model classes. The loader,
   the validator and the model classes only know the current version. Each step is one class in
   `storage/impl/polling/format`.
8. **The registry writes only the current version.** That includes the export to GitOps format.
9. **Every version change ships with:**
   - an entry on the version's upgrade page
   - conversion steps in the user documentation
   - fixtures in the old version, with a test that they still load
   - an update of the example repository, made only after the release, so users of the previous
     release can still follow it

### Timeline for v0 and v1

| Release | `v0` | `v1` |
|---|---|---|
| 3.0 to 3.3 | Current (experimental) | - |
| 3.4 | Deprecated | Current |
| 3.6 at the earliest, or 4.0 | Removed | Current |

A `v2` in 3.x would be introduced at the earliest together with the removal of `v0`, and would deprecate
`v1` in the same release.

## Migrating a repository

A repository may mix versions, so it can be converted file by file.

1. **Rename the types.** From `v0` to `v1` nothing else changes:

   ```bash
   grep -rlE '^\$type: (registry|group|artifact|content)-v0' --include='*.registry.yaml' . \
     | xargs sed -i -E 's/^\$type: (registry|group|artifact|content)-v0$/$type: \1-v1/'
   ```

   Adjust the file pattern if you set `apicurio.polling-storage.file-suffixes`.
2. **Export from a running registry.** A registry that still reads the old version can export its data in
   the current version (`GET /apis/registry/v3/admin/export?format=gitops-v1`, from
   [#10250](https://github.com/Apicurio/apicurio-registry/pull/10250)). This suits simple setups:
   one registry ID, and no need to keep the original file layout. The export lays out files its own way,
   doesn't keep YAML comments, writes a single registry ID, and leaves out fields that it doesn't support
   yet.
3. **Convert only when every reader supports the new version.** A registry that doesn't know a version
   skips files in it (before 3.4) or fails the load (from 3.4). Before 3.4, that means a converted artifact
   disappears from the older registry. Convert the `registry` file first: an older registry then rejects
   the whole load and keeps its last good data, instead of serving the data without the converted files.
4. **Rolling back** to a release that doesn't read the new version means reverting the conversion
   commit.

## Changing the format

- **Adding an optional field:** add it to the model class with a default that keeps the previous
  behaviour, and document the release that added it. No new version is needed.
- **Any other change:**
  1. Add a version to `FormatVersion` as current, and mark the previous one deprecated.
  2. Mark the version before that as removed, or delete it if it's already past its removal release.
  3. Add a `FormatMigration` from the previous version.
  4. Change the model classes to the new version.
  5. Copy one fixture set to the old version, and test it.

  A migration may only use the parsed tree of one file. If a change needs data from several files, it
  needs a different design, which should be recorded in this ADR.

### Behaviour across storage variants

| Variant | Behaviour |
|---|---|
| SQL | Not affected. |
| KafkaSQL | Not affected. |
| GitOps | Reads the files from the Git repository as described above. |
| KubernetesOps | Reads the same files from ConfigMap data, with the same versions and rules. |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Versions | [`FormatVersion`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/format/FormatVersion.java) | The versions and their lifecycle state |
| Migrations | [`FormatMigration`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/format/FormatMigration.java), [`V0ToV1Migration`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/format/V0ToV1Migration.java) | One step from a version to the next |
| Resolution | [`DataFormat`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/format/DataFormat.java) | Parses `$type`, applies the migrations, names the current types |
| Parsing | [`Any`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/model/Any.java) | Binds an upgraded file to the model class |
| Reporting | [`AbstractPollingDataSourceManager`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/AbstractPollingDataSourceManager.java) | Logs the deprecation warning once per load |

## Limitations and open work

1. The deprecation warning is in the logs only. It isn't yet in `/admin/gitops/status` or in the dry-run
   validation result ([#11101](https://github.com/Apicurio/apicurio-registry/issues/11101)).
2. There is no offline converter. The rename above and the export cover `v0` to `v1`
   ([#9946](https://github.com/Apicurio/apicurio-registry/issues/9946) proposes an offline validator,
   which could convert as well).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Keep `v0` as the supported format | Rejected. The name says the format is unstable, and the first real change would then still need everything here. |
| Replace `v0` with `v1` and stop reading `v0` | Rejected. Every existing repository would stop loading on upgrade. |
| A separate `apiVersion` field, as in Kubernetes | Rejected. `$type` already carries the version, and two fields could disagree. |
| One version for the whole repository, in a marker file | Rejected. A repository couldn't be converted gradually, and one repository can serve several registries. |
| Read every past version forever | Rejected. Each version adds a migration and fixtures to maintain, so two readable versions is the limit. |
| Version checks in the loader instead of migrations | Rejected. Checks would spread through the loader and the validator, and the model classes would have to carry every version's fields. |

## Consequences

- **Benefits:** users can upgrade the registry without touching the repository, and convert later, file
  by file. Older replicas never serve partial data because of a newer file. Changing the format later
  is a contained change: one migration class, one fixture set, and the lifecycle table.
- **Costs:** each deprecated version must be kept readable for at least two minor releases, with its
  migration and tests. Readers reject unknown fields, so a new optional field can't be used until every
  reader has it.

## Example and verification

- **User documentation:** [`assembly-configuring-gitops-storage.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-gitops-storage.adoc).

| Concern | Tests to read |
|---|---|
| Type resolution, lifecycle and migrations | `DataFormatTest` |
| `v0`, `v1` and mixed repositories load the same data | `GitOpsFormatVersionsTest` |

```bash
./mvnw test -pl app -Dtest='DataFormatTest,GitOpsFormatVersionsTest'
```

## History

| Milestone | Result |
|---|---|
| 3.0 | `v0` introduced, experimental. |
| [#11101](https://github.com/Apicurio/apicurio-registry/issues/11101) | GitOps becomes supported; `v1` is current and `v0` deprecated. |
