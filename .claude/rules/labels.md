---
paths:
  - "app/src/main/java/**/storage/**/*.java"
  - "app/src/main/java/**/rest/**/*.java"
---
# Labels

Labels are user-editable key/value pairs on groups, artifacts and versions. Several
features also use them to persist their own metadata, which means a feature's data and a
user's data share one namespace. These rules keep the two apart.

## Reserved namespaces

A label key belongs to the registry — not to the user — when it starts with a reserved
prefix. There are two:

| Prefix       | Owner                    | Status                                             |
|--------------|--------------------------|----------------------------------------------------|
| `apicurio.`  | any registry feature     | Use this for new features.                          |
| `contract.`  | data contracts           | Grandfathered. Predates this rule and is persisted in user data, so it is reserved as-is rather than migrated. |

New features must use `apicurio.<feature>.<aspect>`, mirroring the config-property
hierarchy in `config-properties.md`: dots separate levels, hyphens separate words inside
a level.

```java
// Good
public static final String STATUS_MESSAGE_LABEL = "apicurio.mcp-registry.status-message";

// Bad — no namespace, so it is indistinguishable from a user label
public static final String SERVER_VERSION_ID_LABEL = "mcp-server-version-id";
```

Declare keys as constants in one class per feature (see
`io.apicurio.registry.contracts.ContractLabels`), not as inline literals at the point of
use. A feature's keys need to be enumerable for the storage layer to protect them.

## Labels are externally editable

Any caller with `AuthorizedLevel.Write` on an artifact can replace its entire label map
through the v3 metadata endpoints. The storage layer applies that as a wholesale replace,
so a label the caller does not send is deleted.

Two consequences for anything storing metadata in labels:

- **Assume the label can disappear.** Read paths need a defined behaviour when it is
  absent — a documented fallback, or a clear error. Do not assume a label written on
  create is still present on read.
- **Do not store anything security-relevant in a label.** A label is writable by anyone
  with v3 write access to the artifact, which is not necessarily the same set of callers
  a feature's own API authorizes. Storing a state field that gates behaviour — a status,
  an approval, an ownership marker — hands that decision to a different authorization
  path than the one the feature enforces.

Prefer merge-style updates (`RegistryStorage.mergeVersionLabels`, which is scoped by
prefix) over read-modify-write of the whole map, so that concurrent writers to different
namespaces do not clobber each other.

## Storage shape

Labels are persisted twice, and the two copies are not equivalent:

| | Location | Constraints |
|---|---|---|
| Canonical | `versions.labels` / `artifacts.labels` / `groups.labels` (`TEXT`, serialized JSON) | No length limit, case preserved. **This is the read path.** |
| Search index | `version_labels` / `artifact_labels` / `group_labels` (`labelKey VARCHAR(256)`, `labelValue VARCHAR(512)`) | Silently truncated *and* lower-cased on write. |

So a long or mixed-case value round-trips correctly through the API but is only
searchable in truncated, lower-case form. Keep keys under 256 characters and values under
512 if the label needs to be searchable, and never use a label as the store for a
case-sensitive token.
