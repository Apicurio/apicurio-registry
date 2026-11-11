---
paths:
  - "common/src/main/resources/META-INF/openapi.json"
  - "app/src/main/java/**/rest/**/*.java"
  - "app/src/main/java/**/config/**/*.java"
  - "app/src/main/resources/application.properties"
  - "app/src/main/resources/io/apicurio/registry/storage/impl/sql/**"
  - "app/src/main/java/**/storage/impl/sql/upgrader/**/*.java"
  - "operator/model/src/main/java/**/*.java"
  - "schema-util/util-provider/src/main/java/**/*.java"
  - "schema-util/common/src/main/java/**/*.java"
  - "schema-resolver/src/main/java/**/*.java"
  - "serdes/**/src/main/java/**/*.java"
---
# Upgrade impact

These files define what users depend on across an upgrade: the REST contract, configuration,
the database schema, CRD fields, and Java interfaces implemented outside Registry. A change
here can break a running installation or a user's own code. The full guide is
`docs/dev/compatibility.md`.

## Before changing

- REST: keep `openapi.json` additive. A changed status code for a request that clients already
  send is a behavior change even when the spec only gains a response.
- Configuration: don't rename or remove a property in place. Register the old name in
  `DeprecatedPropertiesCheck` (see `.claude/rules/config-properties.md`). Don't change a default
  unless that is the goal of the PR.
- Database: never edit a released upgrade script. Add the next version's script, and an
  `IDbUpgrader` for data changes.
- CRD: keep a replaced field in the schema, and migrate it with an updater in
  `operator/controller/.../updater/`.
- SPIs (`ArtifactTypeUtilProvider*`, `schema-util/common` content interfaces, serdes
  strategies): add methods as `default` methods. An abstract method breaks every external
  implementation.

## Upgrade page

If users who upgrade will notice the change, add an entry in the same PR to the upgrade page of
the version it ships in, `docs/modules/ROOT/pages/getting-started/assembly-upgrading-to-<major>-<minor>.adoc`.
Create the page, and link it from `assembly-versioning-support-policy.adoc` and `nav.adoc`, if
that version has none yet. Each entry says what changed, who is affected, and what to do, and
links the issue or PR. Purely additive, opt-in changes need no entry.
