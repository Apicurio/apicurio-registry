# Issue #9973 — Draft PR: Add Apicurio Registry to `ai-catalog.io/implementations/`

Date: 2026-09-03
Status: **DRAFT ONLY — not submitted.** Per task scope, opening a PR against an external
org's repo (`Agent-Card/ai-catalog`) requires a human decision about how Apicurio wants to
represent itself there. This file captures the exact proposed diff for review.

## Source verified

Fetched the live current content of the file backing
`https://ai-catalog.io/implementations/` from the actual GitHub repo:

```bash
gh api repos/Agent-Card/ai-catalog/contents/docs/implementations.md --jq '.content' | base64 -d
```

Repo: `Agent-Card/ai-catalog`, file: `docs/implementations.md`, default branch: `main`.

Current content (as of 2026-09-03):

```markdown
---
icon: material/code-braces
---

# Implementations

This page lists implementations and tooling for the AI Catalog specification.

## Official

These implementations are maintained by the Agent Card Working Group or its member organisations.

- [spec-works/ai-catalog](https://github.com/spec-works/ai-catalog) (C#, Python)
- [ai-catalog-go-sdk](https://github.com/agntcy/ai-catalog-go-sdk/) (Go)
- [ai-catalog-rust](https://github.com/agntcy/ai-catalog-rust) (Rust)
- [tomevault-io/ai-catalog-reference](https://github.com/tomevault-io/ai-catalog-reference) (Python, Trust Manifest signer/verifier)
- [AI Catalog](https://ai-catalog.outshift.io/) (testbed service)

## Community Projects

Community-built tools, libraries, and integrations. Listed here to help discovery — not formally endorsed by the working group.

!!! tip "Add your project"
    Have an implementation to share? Open a pull request to add it here.
```

The "Community Projects" section is currently empty of entries (only the "add your
project" tip). No prior community PR has landed a bullet entry there yet — checked via
`gh api "repos/Agent-Card/ai-catalog/commits?path=docs/implementations.md"`, which shows
only "Official" entries added so far (`tomevault-io/ai-catalog-reference`, the initial page
scaffold, and the Official/Community split). This PR would be the first Community Projects
entry, so the exact bullet style is inferred from the "Official" section's existing
convention: `[name](url) (short parenthetical description)`.

## Proposed diff

```diff
 ## Community Projects
 
 Community-built tools, libraries, and integrations. Listed here to help discovery — not formally endorsed by the working group.
 
+- [Apicurio Registry](https://github.com/Apicurio/apicurio-registry) (Java, Quarkus-based open-source API/schema registry — serves `/.well-known/ai-catalog.json` and `/.well-known/ard.json` projecting registered A2A `AGENT_CARD` and MCP `MCP_TOOL` artifacts)
+
 !!! tip "Add your project"
     Have an implementation to share? Open a pull request to add it here.
```

## Proposed PR description (for a human to review/edit before submitting)

**Title:** `docs: add Apicurio Registry to Community Projects`

**Body:**

> Apicurio Registry (https://github.com/Apicurio/apicurio-registry) is an open-source,
> Quarkus-based API and schema registry. It implements A2A `AGENT_CARD` and MCP `MCP_TOOL`
> artifact types with governance features (visibility tiers, versioning), and serves both
> `/.well-known/ai-catalog.json` and `/.well-known/ard.json` (per ARD v0.91) projecting its
> registered agent/tool artifacts into the AI Catalog entry envelope. It also implements the
> ARD `POST /search` and `POST /explore` REST endpoints.
>
> This PR adds a single Community Projects entry linking to the project repository.

**Sign-off:** would need a real committer's DCO sign-off, not a fabricated one — flagged as
a human action item.

## What still requires human sign-off before this can actually be submitted

1. **Confirm project representation**: is a bare link to the GitHub repo sufficient, or
   should Apicurio point to a docs page (e.g. the A2A/AI-Catalog integration docs) instead?
2. **Decide whether to wait for the registry-mode conformance gap fix** (see
   `9973-conformance-cli-output.md`) before publicly claiming ARD support in the PR
   description — the `ai-catalog.io` listing itself doesn't require conformance, but the
   PR text implicitly claims "implements ARD search/explore," which is true, so this is a
   judgment call about wording precision, not a blocker.
3. **Fork `Agent-Card/ai-catalog` and open the actual PR** from an authorized GitHub
   identity/account acting on behalf of the Apicurio project — not done by this task.
4. **DCO sign-off** on the real commit, from whoever actually opens the PR.
