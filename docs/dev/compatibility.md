# Compatibility and Breaking-Change Guide for Developers

This guide is for Apicurio Registry **developers and reviewers**. It describes the
areas of the codebase where changes can break users, what the repository currently
does to manage those risks, and proposed policies where no mechanism exists yet.

> **Scope:** This is a developer-facing guide. End-user upgrade, migration, and
> rollback instructions are maintained in the user documentation under
> `docs/modules/ROOT/pages/getting-started/assembly-versioning-support-policy.adoc`
> and `assembly-migrating-registry-v2-v3.adoc`.

> **Policy status:** Sections labeled **Current behavior** are verified from the
> current codebase. Sections labeled **Proposed policy** are recommended rules
> derived from that behavior; they are **not enforced automatically** today.

---

## 1. REST API compatibility

### Current behavior

- The canonical v3 API contract is the OpenAPI document at
  `common/src/main/resources/META-INF/openapi.json`.
- Java v3 resource interfaces are generated from this spec by the
  `apicurio-codegen-maven-plugin` (see `common/pom.xml`).
- Implementations live in `app/src/main/java/io/apicurio/registry/rest/v3/impl/`.
- Client SDKs (`go-sdk/`, `python-sdk/`, `typescript-sdk/`, `java-sdk/`) are
  regenerated from the same OpenAPI spec, usually via Kiota.
- The `.github/workflows/validate-openapi.yaml` CI job lints the spec with
  Spectral when it changes.
- The `.claude/skills/sdk-update/SKILL.md` SDK update skill explicitly reminds
  authors to check backward compatibility.

Because the spec is the source of truth, any change to it is propagated to the
Java interfaces, the SDKs, and the UI. There is no separate compatibility shim.

### Proposed policy

Treat `common/src/main/resources/META-INF/openapi.json` as a public contract.

- **Patch/minor releases:** only additive changes. New request/response fields
  must be optional. New endpoints, query parameters, and optional enum values are
  allowed.
- **Breaking changes** (removing or renaming paths, making formerly optional
  fields required, removing or renaming response fields, removing enum values,
  changing response status codes) require a major-version plan or a documented
  migration period.
- When the spec changes, regenerate the SDKs and update integration tests that
  use generated clients. Follow `.claude/skills/sdk-update/SKILL.md`.
- Run `validate-openapi.yaml` locally before opening a PR that touches the spec.

### What to update when this area changes

- `common/src/main/resources/META-INF/openapi.json`
- Generated Java interfaces (rebuild `common/`)
- Generated SDKs (`go-sdk/`, `python-sdk/`, `typescript-sdk/`, `java-sdk/`)
- This guide, if the compatibility rules themselves change

---

## 2. Operator / CRD compatibility

### Current behavior

- The CRD is defined by the Java model in
  `operator/model/src/main/java/io/apicurio/registry/operator/api/v1/`.
- The CRD is generated at build time by the Fabric8 `crd-generator-apt`
  annotation processor (see `operator/model/pom.xml`).
- Released CRD/install bundles are stored under
  `operator/install/apicurio-registry-operator-<version>.yaml`.
- The operator deployment in those bundles hard-codes matching operand images,
  for example `REGISTRY_APP_IMAGE=quay.io/apicurio/apicurio-registry:3.3.1` in
  `operator/install/apicurio-registry-operator-3.3.1.yaml`.
- The only runtime CRD field migration in the current source is
  `operator/controller/src/main/java/io/apicurio/registry/operator/updater/IngressCRUpdater.java`,
  which rewrites `spec.app.host` / `spec.ui.host` to `spec.app.ingress.host` /
  `spec.ui.ingress.host`.
- The released CRD still contains deprecated `app.sql` and `app.kafkasql`
  fields (described as `DEPRECATED: Use the app.storage.type and
  app.storage.sql / app.storage.kafkasql fields instead`). No Java migration
  code for those fields was found in the current source.

### Proposed policy

- **Do not remove CRD fields without a migration plan.** Kubernetes rejects
  unknown fields once they are removed from the schema, which breaks existing CRs.
- When a field is replaced, keep the old field in the CRD schema for at least
  one minor version and add a migration updater under
  `operator/controller/src/main/java/io/apicurio/registry/operator/updater/`.
- Add or update `operator/controller/src/test/java/io/apicurio/registry/operator/it/CRUpdateITTest.java`
  cases for each migration.
- Do not add new **required** CRD fields unless users can leave them empty and
  receive a sensible default.
- Do not change the semantics of an existing field without changing its name or
  adding a clear version gate.

### What to update when this area changes

- `operator/model/src/main/java/io/apicurio/registry/operator/api/v1/spec/*.java`
- `operator/controller/src/main/java/io/apicurio/registry/operator/updater/*`
- `operator/controller/src/test/java/io/apicurio/registry/operator/it/CRUpdateITTest.java`
- `operator/install/apicurio-registry-operator-<version>.yaml` (generated, but
  reviewed before release)
- User docs: `docs/modules/ROOT/pages/getting-started/assembly-operator-config-reference.adoc`

---

## 3. Configuration compatibility

### Current behavior

- Registry configuration properties are declared with `@ConfigProperty` and
  documented with `@Info` in the `app` module (e.g. `RestConfig.java`,
  `AuthConfig.java`, `ElasticsearchSearchConfig.java`).
- The `@Info` annotation lives in
  `config-index/definitions/src/main/java/io/apicurio/common/apps/config/Info.java`.
- It supports `category`, `description`, `availableSince`, `experimental`,
  `seeAlso`, and `dependsOn`. It does **not** support a deprecated or renamed
  flag.
- Experimental boolean properties are gated globally by
  `apicurio.features.experimental.enabled`, enforced by
  `app/src/main/java/io/apicurio/registry/config/ExperimentalFeaturesConfig.java`.
- The configuration reference document
  `docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc`
  is generated by
  `docs/config-generator/src/main/java/io/apicurio/registry/docs/GenerateAllConfigPartial.java`
  from Jandex.
- `.claude/rules/config-properties.md` is the project convention for new
  properties.

### Proposed policy

- Do not rename existing configuration keys. If a new behavior needs a new key,
  add it and keep the old key for backward compatibility where possible.
- Do not change default values unless that is the explicit goal of the PR and is
  called out in the release notes.
- Mark every new `@ConfigProperty` with `@Info` and an accurate
  `availableSince` version.
- Mark experimental properties with `@Info(experimental = true)` and require the
  global experimental gate.
- When changing a property, regenerate the config docs:
  `./mvnw clean install -pl :apicurio-registry-config-generator -am -DskipTests`
  and commit the updated `ref-registry-all-configs.adoc`.

### What to update when this area changes

- The relevant `*Config.java` class in `app/src/main/java/...`
- `app/src/main/resources/application.properties` if the default must be set there
- `docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc`
- This guide, if the property rules change

---

## 4. Database migrations

### Current behavior

- The SQL storage layer is forward-only. The current DB version is stored in
  `app/src/main/resources/io/apicurio/registry/storage/impl/sql/db-version`.
- Upgrade scripts live in
  `app/src/main/resources/io/apicurio/registry/storage/impl/sql/upgrades/<version>/<dbtype>.upgrade.ddl`.
- Advanced data migrations are implemented as Java classes implementing
  `IDbUpgrader` in
  `app/src/main/java/io/apicurio/registry/storage/impl/sql/upgrader/`.
- On startup, `AbstractSqlRegistryStorage` checks the DB version and runs
  `upgradeDatabaseRaw(...)` if it is behind. It throws an error if an older
  Registry binary is started against a newer DB schema.
- There are **no** downgrade scripts.
- End-user documentation in
  `docs/modules/ROOT/pages/getting-started/assembly-versioning-support-policy.adoc`
  already states that downgrades are unsupported and that users must restore
  from backup.

### Proposed policy

- Keep schema changes additive. New columns should be nullable or have defaults
  that older code can ignore.
- Do not drop or rename columns, tables, or constraints unless the old schema is
  no longer supported by any supported minor version.
- When data backfills are required, provide an `IDbUpgrader` implementation and
  add a test for the upgrade path.
- Never commit manual rollback scripts. The recovery path is: stop Registry,
  restore the pre-upgrade database backup, deploy the previous version.
- Document any migration that requires user action (e.g., re-indexing, import/export).

### What to update when this area changes

- `app/src/main/resources/io/apicurio/registry/storage/impl/sql/db-version`
- `app/src/main/resources/io/apicurio/registry/storage/impl/sql/upgrades/<new-version>/<dbtype>.upgrade.ddl`
- `app/src/main/java/io/apicurio/registry/storage/impl/sql/upgrader/*` if data
  migration is needed
- Storage variant test coverage for all SQL dialects (H2, PostgreSQL, MySQL, MSSQL)

---

## 5. Experimental features

### Current behavior

- The global experimental gate is `apicurio.features.experimental.enabled`.
- Boolean experimental properties are auto-discovered at build time from
  `@Info(experimental = true)`. Non-boolean experimental features (e.g.,
  `apicurio.storage.kind=gitops` or `kubernetesops`) are checked explicitly in
  `ExperimentalFeaturesConfig.validate()`.
- The GitOps storage README at
  `app/src/main/java/io/apicurio/registry/storage/impl/gitops/README.md` states
  that the data format (`*-v0`) may change in future releases.
- There is no graduation or deprecation mechanism for experimental features
  beyond removing the `experimental` flag and updating documentation.

### Proposed policy

- New features that are not yet API-, schema-, or data-format-stable must be
  marked experimental.
- Keep experimental features gated until:
  - the data format is declared stable,
  - the configuration surface is declared stable,
  - the feature is covered by tests for all relevant storage/deployment variants,
  - user documentation exists.
- When an experimental feature graduates, remove `@Info(experimental = true)` and
  update the description, release notes, and user docs. Do not change the
  configuration key unless absolutely necessary; just remove the gate.
- Before removing or changing an experimental feature, announce it in the
  release notes because users may have enabled the gate.

### What to update when this area changes

- The relevant `@ConfigProperty`/`@Info` definitions
- `app/src/main/java/io/apicurio/registry/config/ExperimentalFeaturesConfig.java`
  if a non-boolean experimental feature is added
- `docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc`
- User docs for the feature
- Release notes

---

## 6. Operator ↔ Registry compatibility

### Current behavior

- The operator does not select a Registry version dynamically. It uses the
  container images provided by environment variables:
  `REGISTRY_APP_IMAGE`, `REGISTRY_UI_IMAGE`, `REGISTRY_GITOPS_SYNC_IMAGE`, and
  `REGISTRY_CONSOLE_PLUGIN_IMAGE`.
- These values are set in the released install YAML to the same version as the
  operator (e.g., operator 3.3.1 ships with operand images tagged 3.3.1).
- `operator/controller/src/main/java/io/apicurio/registry/operator/Configuration.java`
  reads `registry.app.image`, `registry.ui.image`, etc., falling back to
  `related.image.registry.app.image`.
- The `registry.version` property is used only for logging and Kubernetes labels,
  not for image selection.
- There is no runtime compatibility matrix or capability check between the
  operator and the operand.
- Advanced users can override the operand image through
  `spec.(app/ui).podTemplateSpec.spec.containers[name=...].image`, but the
  operator README explicitly marks this as **not recommended**.

### Proposed policy

- Treat the operator and its operand images as a single release unit. The
  released install YAML is the supported combination.
- Do not override the operand image to an arbitrary version. The operator may
  inject environment variables or create resources based on CRD fields that an
  older or newer Registry version does not understand, and there is no runtime
  validation to catch the mismatch.
- When adding new CRD fields that map to Registry configuration, assume the
  operator will only support Registry versions that understand those keys.
- If a future compatibility matrix is introduced, it belongs here and in the
  operator docs.

### What to update when this area changes

- `operator/controller/src/main/java/io/apicurio/registry/operator/Configuration.java`
- `operator/install/apicurio-registry-operator-<version>.yaml`
- `operator/Makefile` image variables
- `docs/modules/ROOT/pages/getting-started/assembly-deploying-registry-operator.adoc`

---

## 7. Reviewer checklist

When reviewing a PR, check the compatibility impact of the affected areas:

- [ ] **REST API:** Does the PR change `openapi.json`? If so, are changes
      additive? Are SDKs regenerated?
- [ ] **Operator / CRD:** Does the PR remove or rename CRD fields? If so, is a
      migration updater provided and tested in `CRUpdateITTest`?
- [ ] **Configuration:** Does the PR rename or change defaults for existing
      properties? Are `@Info` annotations and the generated config docs updated?
- [ ] **Database:** Does the PR add or modify SQL schema? Are forward-only
      migration scripts and any Java upgraders provided for all SQL dialects?
- [ ] **Experimental features:** Is the feature marked experimental? Is the gate
      honored? Is graduation documented?
- [ ] **Operator ↔ Registry:** Does the PR change the environment variables or
      CRD fields the operator maps to the operand? If so, is the operand version
      coupling documented?

---

## 8. Related documentation and conventions

- User-facing versioning and support policy:
  `docs/modules/ROOT/pages/getting-started/assembly-versioning-support-policy.adoc`
- v2 → v3 migration guide:
  `docs/modules/ROOT/pages/getting-started/assembly-migrating-registry-v2-v3.adoc`
- Schema compatibility modes (for artifact content, not project compatibility):
  `docs/modules/ROOT/pages/getting-started/assembly-registry-compatibility-modes.adoc`
- Configuration property conventions:
  `.claude/rules/config-properties.md`
- SDK update workflow:
  `.claude/skills/sdk-update/SKILL.md`
- Contributor checklist: `CLAUDE.md`
- Automated code reviewer prompt: `.claude/agents/code-reviewer.md`
