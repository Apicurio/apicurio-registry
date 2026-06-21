# Apicurio Registry Governance

Apicurio Registry is a [CNCF Sandbox](https://www.cncf.io/projects/) project.

This document describes the governance structure for the Apicurio Registry project,
including roles, decision-making processes, and how we ensure vendor-neutral stewardship.

## Principles

- **Open:** All project activity happens in public (GitHub issues, PRs, mailing lists, community calls).
- **Welcoming:** We follow the [CNCF Code of Conduct](CODE_OF_CONDUCT.md) and actively encourage participation from anyone.
- **Transparent:** Decisions are made in the open and documented. Non-trivial changes go through public pull requests with review.
- **Vendor-neutral:** No single company controls the project's direction. Governance decisions are made by the maintainer body, not by any individual organization.

## Roles

### Contributor

Anyone who contributes to the project (code, documentation, bug reports, reviews,
community support). No formal nomination is needed.

**Responsibilities:**
- Follow the [Code of Conduct](CODE_OF_CONDUCT.md) and [Contributing Guide](CONTRIBUTING.md)
- Sign off commits using the [DCO](https://developercertificate.org/)

### Reviewer

A contributor who has demonstrated consistent, quality contributions in a specific
area of the project and has been granted review permissions.

**Responsibilities:**
- Review pull requests in their area of expertise
- Provide timely, constructive feedback
- Help contributors improve their submissions

**How to become a reviewer:**
- Demonstrate sustained contributions over at least 3 months
- Nominated by a maintainer, approved by lazy consensus among maintainers

### Maintainer

Maintainers are the core decision-makers for the project. They have write access
to project repositories and are responsible for the project's overall health and direction.

**Responsibilities:**
- Set project direction and priorities
- Review and merge pull requests
- Participate in release management
- Mentor reviewers and contributors
- Respond to security reports
- Represent the project in CNCF and community forums

**How to become a maintainer:**
- Serve as a reviewer for at least 6 months
- Demonstrate deep understanding of the codebase and project goals
- Nominated by an existing maintainer
- Approved by supermajority (2/3) vote of current maintainers

**Removal / Emeritus:**
A maintainer may step down voluntarily at any time by notifying the other maintainers.
A maintainer who has been inactive for 12 months may be moved to emeritus status by
a majority vote of the remaining active maintainers. Emeritus maintainers are
recognized for their past contributions but do not have voting rights or write access.
Emeritus maintainers can regain active status through the standard nomination process.

### Project Lead

The project lead is a maintainer elected by the maintainer body to serve as the
primary point of contact for the CNCF and to facilitate governance processes.
The project lead does not have special decision-making authority beyond that of
any other maintainer.

**Term:** 1 year, renewable by maintainer vote.

## Current Maintainers

| Name | GitHub | Affiliation |
|---|---|---|
| Eric Wittmann | [@EricWittmann](https://github.com/EricWittmann) | Red Hat |
| Jakub Senko | [@jsenko](https://github.com/jsenko) | Red Hat |
| Carles Arnal | [@carlesarnal](https://github.com/carlesarnal) | Red Hat |
| Andrea Peruffo | [@andreaTP](https://github.com/andreaTP) | Red Hat |

We actively encourage maintainer nominations from other organizations. Contributor
diversity is a project health goal — see [ADOPTERS.md](ADOPTERS.md) for organizations
using Apicurio Registry.

## Decision Making

### Lazy Consensus

Most decisions are made through lazy consensus on pull requests and issues. A proposal
is considered accepted if:
- It has been open for at least 72 hours (or 1 week for significant changes)
- At least one maintainer has approved it
- No maintainer has objected

### Voting

For decisions that cannot be resolved by lazy consensus, a formal vote is held:
- Each maintainer gets one vote, regardless of organizational affiliation
- Simple majority wins for routine decisions
- Supermajority (2/3) is required for:
  - Adding or removing maintainers
  - Changes to governance
  - Major architectural decisions
  - Licensing changes
- Votes are held on the project mailing list or in a GitHub issue and remain open for at least 1 week

### Vendor Neutrality

To ensure no single organization controls the project:
- All maintainers vote as individuals, not as representatives of their employers
- Roadmap and feature prioritization decisions are made by the maintainer body as a whole
- No organization can veto a decision approved by the maintainer body
- We actively work to grow the maintainer base across organizations

## Subprojects

The Apicurio Registry project includes several subprojects, each with their own
area of responsibility:

| Subproject | Repository / Path | Area |
|---|---|---|
| Core Server | `app/`, `common/`, `storage/` | API server, storage backends |
| User Interface | `ui/` | React-based web UI |
| Kubernetes Operator | `operator/` | Operator for K8s deployment |
| Java SDK | `java-sdk/` | Java client library |
| Go SDK | `go-sdk/` | Go client library |
| Python SDK | `python-sdk/` | Python client library |
| TypeScript SDK | `typescript-sdk/` | TypeScript client library |
| Serializers/Deserializers | `serdes/` | Kafka, NATS, Pulsar serdes |
| CLI | `cli/` | Command-line interface |
| MCP Server | `mcp/` | Model Context Protocol server |

Subprojects follow the same governance as the main project. As the contributor base
grows, subprojects may elect their own leads from among the maintainers.

## Conflict Resolution

If a conflict cannot be resolved within the maintainer body, it may be escalated to:
1. The project lead for mediation
2. The CNCF Technical Oversight Committee (TOC) as a last resort

## Changes to Governance

Changes to this document require a pull request reviewed and approved by supermajority
(2/3) of the maintainer body. The PR must remain open for at least 2 weeks to allow
community feedback.

## Code of Conduct

This project follows the [CNCF Code of Conduct](CODE_OF_CONDUCT.md). See that document
for reporting procedures and enforcement details.
