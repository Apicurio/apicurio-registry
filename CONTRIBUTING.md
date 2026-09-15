# Contributing guide

**Want to contribute? Great!**
We try to make it easy, and all contributions, even the smaller ones, are more than welcome.
This includes bug reports, fixes, documentation, examples...
But first, read this page (including the small print at the end).

* [Legal](#legal)
* [Reporting an issue](#reporting-an-issue)
* [Getting started and where to ask](#getting-started-and-where-to-ask)
* [Before you contribute](#before-you-contribute)
  + [Code reviews](#code-reviews)
  + [Coding Guidelines](#coding-guidelines)
  + [Continuous Integration](#continuous-integration)
  + [Tests and documentation are not optional](#tests-and-documentation-are-not-optional)
* [The small print](#the-small-print)


## Legal

All original contributions to Apicurio projects are licensed under the
[ASL - Apache License](https://www.apache.org/licenses/LICENSE-2.0),
version 2.0 or later, or, if another license is specified as governing the file or directory being
modified, such other license.

All contributions are subject to the [Developer Certificate of Origin (DCO)](https://developercertificate.org/).
The DCO text is also included verbatim in the [dco.txt](dco.txt) file in the root directory of the repository.

## Reporting an issue

This project uses GitHub issues to manage the issues. Open an issue directly in GitHub.

If you believe you found a bug, and it's likely possible, please indicate a way to reproduce it, what you are seeing and what you would expect to see.
Don't forget to indicate your Apicurio Registry, Java, and Maven versions.

For security vulnerabilities, please do not use GitHub issues. Instead, email
[cncf-apicurio-registry-security@lists.cncf.io](mailto:cncf-apicurio-registry-security@lists.cncf.io).
See [SECURITY.md](SECURITY.md) for details.

For general questions and development discussions, use the
[cncf-apicurio-registry-dev@lists.cncf.io](mailto:cncf-apicurio-registry-dev@lists.cncf.io) mailing list
or the [#apicurio channel](https://cloud-native.slack.com/archives/C0BDWTC1DTM) on CNCF Slack.

## Getting started and where to ask

New here? This section points you to the right place for whatever you need.

**Not sure where a question belongs?** [SUPPORT.md](SUPPORT.md) maps each kind of question or report to the channel that will answer it fastest, from usage questions and bugs to feature ideas and security reports.

**Looking for a first issue?** Browse issues labelled [`good first issue`](https://github.com/Apicurio/apicurio-registry/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) or [`help wanted`](https://github.com/Apicurio/apicurio-registry/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22). When you find one, follow [Claiming an issue](#claiming-an-issue) to self-assign before you start, and ask in the issue if anything is unclear.

**Building and running the project** is covered in [DEVELOPING.md](DEVELOPING.md): prerequisites, build options, running the server, running tests, and IDE setup.

**New to open source, or joining through a mentorship program?** You are very welcome. Apicurio Registry is a CNCF Sandbox project and takes part in programs such as [LFX Mentorship](https://mentorship.lfx.linuxfoundation.org/) and [CNCF Mentoring](https://github.com/cncf/mentoring); general guidance for new CNCF contributors is collected at [contribute.cncf.io](https://contribute.cncf.io/). The guidelines on this page apply equally to mentees and first-time contributors. When in doubt, just ask in the issue or on Slack.

## Before you contribute

To contribute, use GitHub Pull Requests from your **own** fork.

Also, make sure you have set up your Git authorship correctly:

```bash
git config --global user.name "Your Full Name"
git config --global user.email your.email@example.com
```
If you use different computers to contribute, please make sure the name is the same on all your computers.

We may use this information to acknowledge your contributions!

### Compatibility

Before opening a PR, review the [Developer Compatibility Guide](docs/dev/compatibility.md) when your change affects the REST API, Operator CRDs, configuration properties, database migrations, experimental features, or Operator/Registry compatibility.

Changes with compatibility impact should include appropriate migration or documentation updates.

## Claiming an issue

Before you start working on an issue, let us know so we don't end up with duplicate effort:

1. **Comment on the issue** using `/assign-me` (or `/claim`) to self-assign, or ask if you have questions before claiming.
2. **Assignment Limit:** Contributors can have a maximum of 3 open issues assigned concurrently. Use `/unassign-me` to release an issue.
3. **If someone is already assigned**, don't open a competing PR — ask in the issue whether they need help or have moved on.
4. **Stale assignments:** if an assigned issue has no PR and no update for two weeks, comment asking for a status update. If there's no response within a few days, a maintainer can reassign it.

Opening a PR on an issue that's assigned to someone else without checking first is likely to get your PR closed.

### Code reviews

All submissions, including submissions by project members, need to be reviewed by at least one Apicurio committer before being merged.

[GitHub Pull Request Review Process](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/reviewing-changes-in-pull-requests/about-pull-request-reviews) is followed for every pull request.

### Coding Guidelines

 * We primarily use the Git history to track authorship. GitHub also has [this nice page with your contributions](https://github.com/Apicurio/apicurio-registry/graphs/contributors).
 * Please take care to write code that fits with existing code styles.  For your convenience we have Formatters and/or Code Templates for both [Eclipse](https://github.com/Apicurio/apicurio-configs/tree/main/eclipse) and [IntelliJ](https://github.com/Apicurio/apicurio-configs/tree/main/intellij).
 * Commits should be atomic and semantic. Please properly squash your pull requests before submitting them. Fixup commits can be used temporarily during the review process but things should be squashed at the end to have meaningful commits.
 * We typically squash and merge pull requests when they are approved.  This tends to keep the commit history a little bit more tidy without placing undue burden on the developers.

### Continuous Integration

Because we are all humans, and to ensure Apicurio Registry is stable for everyone, all changes must pass continuous integration before being merged. Apicurio CI is based on GitHub Actions, which means that pull requests will receive automatic feedback.  Please watch out for the results of these workflows to see if your PR passes all tests.

CI runs in two tiers:

1. **Fast gate** (`Quick Check` workflow, ~5 min): runs on every push to every PR,
   regardless of author or review state. Compiles the project (including test
   sources) and runs the pure unit tests plus a curated app smoke set. This is what
   gives you rapid feedback while iterating.
2. **Full verification** (`Verify` workflow): the complete suite — build, unit
   tests, CLI, SDKs, console plugin, integration tests, extra tests, operator
   tests, and the Verification Gate (the single required check for merging). It
   runs immediately for maintainers and other trusted authors (e.g. Renovate), or
   once your PR has an approving review otherwise — not gated by any label a
   maintainer has to apply. It also always runs on every push to `main`. If it
   fails, the PR reverts to `lifecycle/ready-for-review` and `lifecycle/tested` is
   cleared so it's clear a fresh fast-gate pass and review are needed again.

### Tests and documentation are not optional

Don't forget to include tests in your pull requests.
Also don't forget the documentation (reference documentation, javadoc...).

Be sure to test your pull request against the storage variants your change affects.

Since Apicurio Registry 3.0 a single build supports every storage variant, so the
variant is selected at runtime rather than by a Maven profile
(see [DEVELOPING.md](DEVELOPING.md#build-configuration)):

| Storage variant                | Selected with                                                            |
|--------------------------------|--------------------------------------------------------------------------|
| SQL                            | `-Dapicurio.storage.kind=sql` (default)                                   |
| KafkaSQL                       | `-Dapicurio.storage.kind=kafkasql`                                        |
| GitOps (experimental)          | `-Dapicurio.storage.kind=gitops`                                          |
| Kubernetes ConfigMap (experimental) | `-Dapicurio.storage.kind=kubernetesops`                              |

For the SQL variant, the database flavor is chosen separately with
`-Dapicurio.storage.sql.kind`, which accepts `h2` (default), `postgresql`, `mssql`,
and `mysql`.

Storage-specific unit tests live under the matching packages and can be run directly:

```bash
./mvnw test -pl app -Dtest='io.apicurio.registry.storage.impl.kafkasql.**'
```

Integration tests are opt-in and are documented in the
[integration tests module](integration-tests/):

```bash
./mvnw verify -Pintegration-tests -pl integration-tests -am
```

CI runs the full storage matrix as the pre-merge gate, so running every variant
locally is not required.

### Customizing Registry supported ArtifactTypes

The artifact types supported by a registry instance can be configured at deployment time, without
changing the registry code, through a JSON file referenced by `apicurio.artifact-types.config-file`.
Each custom type delegates its behaviour (content detection, validation, compatibility checking,
canonicalization, ...) either to **webhooks** or to **Java classes** implementing the interfaces of
`apicurio-registry-schema-util-common` (`ContentAccepter`, `ContentValidator`, `CompatibilityChecker`, ...).

Java providers are added to the container image by deriving from the `apicurio/apicurio-registry:VERSION-mutable`
image (a re-augmentable Quarkus mutable-jar, produced with `-Dfull`), copying the jar into
`/deployments/quarkus-app/providers/` and running `/deployments/build.sh`. See the
["Configuring custom artifact types"](docs/modules/ROOT/pages/getting-started/assembly-custom-artifact-types.adoc)
documentation and the [custom-artifact-types example](examples/custom-artifact-types/) for a complete walkthrough.

**NOTE:** The registry UI shows the plain name of a custom type and has no dedicated icon for it.

## Versioning & Release Cycle

Apicurio Registry uses [Semantic Versioning](https://semver.org/) with a clear split between minor and patch releases:

| Release type | Contains | Example |
|--------------|----------|---------|
| **Minor** (3.3.0 → 3.4.0) | New features, enhancements, bug fixes | Scheduled development milestones |
| **Patch** (3.3.0 → 3.3.1) | CVE / security fixes only | Dependency upgrades, security patches |

### Support window

We maintain the **latest two minor versions** with security patches. Once a new minor is released, the oldest supported minor reaches end-of-life.

### Branch strategy

| Branch | Purpose |
|--------|---------|
| `main` | Active development — next minor release |
| `3.3.x` | Maintenance branch for 3.3 patch releases (created when 3.4.0 development starts) |

### Where to target your PR

- **Features and bug fixes** → target `main`
- **CVE / security backports for N-1** → target the maintenance branch (e.g., `3.3.x`), cherry-picked from the fix on `main`
- Bug fix backports are **not** accepted on maintenance branches — patches are strictly CVE-only

## The small print

This project is an open source project. Please act responsibly, be nice, polite and enjoy!
