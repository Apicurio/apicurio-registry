# Apicurio Registry — CNCF Incubation Gap Analysis

**Date:** 2026-06-15
**Current status:** Sandbox (onboarding in progress)
**Target:** Incubating

This document identifies the gaps between Apicurio Registry's current state and the
CNCF Incubation criteria, and proposes actions to close each gap.

---

## 1. Governance

**Requirement:** Clear, documented, and discoverable project governance that demonstrates
vendor-neutral direction.

**Current state:** No `GOVERNANCE.md` exists. `CONTRIBUTING.md` covers contribution
mechanics (PRs, DCO, code style) but does not define project roles, decision-making
processes, or vendor-neutrality safeguards.

**Gap:** Missing formal governance document.

**Actions:**
- [ ] Create `GOVERNANCE.md` covering:
  - Project roles and responsibilities (Maintainer, Committer, Contributor)
  - Criteria for earning and revoking each role
  - Decision-making process (e.g., lazy consensus with escalation to maintainer vote)
  - Vendor-neutrality statement (no single company holds veto power)
  - Subproject ownership (operator, SDKs, UI, etc.)
- [ ] Add a `MAINTAINERS.md` listing current maintainers with affiliations

---

## 2. Contributor Diversity

**Requirement:** Committers from multiple organizations, demonstrating the project can
survive the departure of any single company.

**Current state (last 12 months, excluding bots):**

| Contributor | Commits | Affiliation |
|---|---|---|
| Eric Wittmann | 529 | Red Hat |
| Jakub Senko | 291 | Red Hat |
| Carles Arnal | 170 | Red Hat |
| Andrea Peruffo | 21 | Red Hat |
| Others (community) | ~50 | Various |

~96% of non-bot commits come from Red Hat employees.

**Gap:** Contributor base is heavily concentrated in a single organization. This is
typically the hardest criterion to satisfy and the one TOC reviewers scrutinize most.

**Actions:**
- [ ] Actively recruit maintainers/committers from adopter organizations (Axual, IBM,
      Castor, Libon)
- [ ] Create "good first issue" and "help wanted" labels with mentoring support
- [ ] Consider a contributor ladder (Contributor → Reviewer → Committer → Maintainer)
      to give community members a clear path
- [ ] Run contributor onboarding sessions or office hours
- [ ] Track contributor-org diversity as a project health metric

---

## 3. Adopters

**Requirement:** At least 3 independent adopters willing to be interviewed by the TOC.

**Current state:** `ADOPTERS.md` lists 6 organizations:

| Organization | Type |
|---|---|
| Axual | Vendor |
| Castor | End-user |
| IBM | Vendor |
| Libon | End-user |
| Red Hat | Vendor |
| ZenWave 360 | End-user |

**Status:** Likely sufficient on paper. Red Hat may not count as "independent" given its
role as primary maintainer.

**Actions:**
- [ ] Confirm at least 3 non-Red Hat adopters are willing to participate in TOC
      interviews
- [ ] Grow the adopter list — 814 GitHub stars and 320 forks suggest broader adoption
      than what's documented
- [ ] Reach out to known users via GitHub Discussions, community calls, or social media

---

## 4. Code of Conduct

**Requirement:** Adopt the CNCF Code of Conduct (or an equivalent explicitly based on it).

**Current state:** `CODE_OF_CONDUCT.md` uses Contributor Covenant v1.4. Enforcement
contact is `apicurio@lists.jboss.org`.

**Gap:** CNCF expects projects to either adopt the
[CNCF Code of Conduct](https://github.com/cncf/foundation/blob/main/code-of-conduct.md)
or reference it explicitly. The current CoC version (1.4) is also outdated — Contributor
Covenant is now at v2.1. The enforcement contact should reference CNCF's conduct
committee as an escalation path.

**Actions:**
- [ ] Update to CNCF Code of Conduct or Contributor Covenant v2.1 with a CNCF
      escalation clause
- [ ] Update the enforcement contact to include CNCF conduct reporting alongside
      project-level contacts

---

## 5. Security

**Requirement:** Responsible vulnerability reporting process and proactive security posture.

**Current state:**
- `SECURITY.md` exists with private reporting instructions (email to
  `apicurio.registry@redhat.com`)
- Trivy image scanning via GitHub Actions
- GitHub Security Advisories enabled

**Status:** Mostly covered. No formal third-party security audit.

**Actions:**
- [ ] Request a CNCF-funded security audit (available to Sandbox/Incubating projects via
      CNCF/OSTIF)
- [ ] Enable GitHub code scanning (CodeQL or equivalent SAST) on PRs
- [ ] Consider adding SBOM generation to release pipeline
- [ ] Document the security response process (SLA for triage, fix timelines)

---

## 6. TAG Engagement

**Requirement:** Engage with relevant CNCF Technical Advisory Groups (TAGs).

**Current state:** No known engagement with CNCF TAGs.

**Gap:** The project has not presented to or engaged with the relevant TAG. Apicurio
Registry fits under **TAG App Delivery** (runtime infrastructure, developer tooling) and
potentially **TAG Security** (for the auth/RBAC aspects).

**Actions:**
- [ ] Present Apicurio Registry at a TAG App Delivery meeting
- [ ] Engage with TAG Security for security review guidance
- [ ] Participate in TAG-sponsored working groups or SIGs where relevant

---

## 7. Documentation

**Requirement:** Installation docs, end-user docs, reference implementations, and
documented integrations.

**Current state:**
- Installation docs exist (Operator, Docker, Quarkus dev mode)
- User documentation at apicurio.io
- SDK examples across Java, Go, Python, TypeScript
- Integrations documented (Kafka, NATS, Pulsar serdes)

**Status:** Likely sufficient. May need review for completeness and freshness.

**Actions:**
- [ ] Audit docs for completeness — ensure all storage variants are covered
- [ ] Ensure getting-started guides work end-to-end for new users
- [ ] Add architecture/design docs for contributors (not just users)

---

## 8. Vendor-Neutral Branding

**Requirement:** Project metadata, website, and resources must appear vendor-neutral.

**Current state:**
- GitHub org is `Apicurio` (not Red Hat-branded) — good
- Security contact is `apicurio.registry@redhat.com` — Red Hat branded
- CoC contact is `apicurio@lists.jboss.org` — JBoss/Red Hat branded
- Some CI infrastructure may reference Red Hat-internal systems

**Gap:** Contact addresses and some infrastructure carry Red Hat branding.

**Actions:**
- [ ] Set up a vendor-neutral contact email (e.g., via CNCF mailing list or
      `security@apicurio.io`)
- [ ] Migrate CoC contact away from `lists.jboss.org`
- [ ] Review CI/CD for references to Red Hat-internal infrastructure
- [ ] Ensure the project website and social media are community-owned

---

## 9. OpenSSF Best Practices Badge

**Requirement:** Not required for Incubation (only for Graduation), but starting early is
strongly recommended.

**Current state:** No OpenSSF badge registered.

**Actions:**
- [ ] Register at https://www.bestpractices.dev/ and begin working toward "Passing" level
- [ ] Many criteria are already met (OSS license, DCO, CI, security policy, issue tracker)
- [ ] Address any gaps now to ease the path to Graduation later

---

## Priority Summary

| Priority | Item | Effort | Impact |
|---|---|---|---|
| **P0** | Governance document | Medium | Blocker — required for application |
| **P0** | Contributor diversity | High | Blocker — most scrutinized criterion |
| **P0** | Adopter interview readiness | Low | Confirm existing adopters will participate |
| **P1** | Code of Conduct update | Low | Quick win |
| **P1** | Vendor-neutral contacts | Low | Quick win |
| **P1** | TAG engagement | Medium | Needed before application |
| **P2** | Security audit | Medium | CNCF funds this — just apply |
| **P2** | OpenSSF badge | Medium | Not required but recommended |
| **P2** | Documentation audit | Low | Likely already sufficient |

---

## Estimated Timeline

Assuming active work starts now:

- **Months 1-2:** Governance docs, CoC update, vendor-neutral contacts, adopter outreach,
  OpenSSF badge registration
- **Months 3-6:** TAG presentations, contributor recruitment programs, security audit
  application
- **Months 6-12:** Build contributor diversity track record, accumulate non-Red Hat
  committers
- **Month 12+:** Apply for Incubation once multi-org contributor base is demonstrable

**Realistic target for Incubation application: Q2-Q3 2027**, with contributor diversity
being the primary pacing factor.
