<!--
Thank you for contributing to Apicurio Registry.

Please read the contributing guide before opening this PR:
https://github.com/Apicurio/apicurio-registry/blob/main/CONTRIBUTING.md

The PR lifecycle is managed by labels and comment commands:
https://github.com/Apicurio/apicurio-registry/blob/main/.github/PR_LIFECYCLE.md
-->

## Description

<!--
Summarize the change and include the relevant context, root cause, or design choice.
-->

## Related Issue

<!-- Use "Fixes #1234" or "Closes #1234" when the PR resolves an issue. -->

Fixes #

## Type of Change

- [ ] Bug fix
- [ ] New feature or enhancement
- [ ] Documentation update
- [ ] Refactoring, build, or CI change
- [ ] Other

## Target Branch

- [ ] `main` for features, enhancements, and bug fixes
- [ ] Maintenance branch for CVE or security backports only

## Verification

<!--
List the commands you ran, manual checks you performed, or explain why testing is not applicable.

For storage-sensitive changes, verify the relevant SQL, KafkaSQL, KubernetesOps, or GitOps paths.
The current CI storage matrix and remote integration-test profiles are documented in
.github/workflows/README.md.
-->

- [ ] Added or updated tests for this change, or explained why tests are not needed
- [ ] Ran relevant local verification
- [ ] `./mvnw checkstyle:check -pl <module>` passes, or is not applicable
- [ ] Verified storage-specific behavior where applicable
- [ ] Verified UI, Console Plugin, Operator, CLI, or SDK behavior where applicable

## Documentation

- [ ] Updated relevant documentation, API references, examples, or Javadocs
- [ ] No documentation update is needed

## Checklist

- [ ] I have read `CONTRIBUTING.md`
- [ ] The linked issue is assigned to me, or I commented and got maintainer approval first
- [ ] I have performed a self-review
- [ ] My commit messages follow Conventional Commits (`type(scope): description`)
- [ ] My commits include a DCO sign-off (`Signed-off-by:`)
- [ ] I understand a maintainer must accept this PR before the full CI suite runs (see `.github/PR_LIFECYCLE.md`)
