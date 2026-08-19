# Apicurio Registry

Open-source API and Schema Registry. Apache 2.0 license, DCO sign-off required on all commits.

## Build & Test

See [DEVELOPING.md](DEVELOPING.md) for build tiers, build properties, testing, and IDE setup.
See [README.md](README.md) for a quick-start guide.

## Architecture

Multi-module Maven project. Quarkus-based. Storage variants and build configuration are
documented in [DEVELOPING.md](DEVELOPING.md).

Storage implementations: `app/src/main/java/io/apicurio/registry/storage/impl/`

## Conventions

### Commit Messages

Conventional Commits format: `<type>(<scope>): <description> (#PR)`
Types: `feat`, `fix`, `chore`, `docs`, `ci`, `test`, `refactor`.
See [CONTRIBUTING.md](CONTRIBUTING.md) for full commit and PR guidelines.

### Code Style

Checkstyle config: `.checkstyle/checkstyle.xml` (the only config the build uses).
It is **tiered** — read the comments in that file before assuming a rule is enforced.

**Enforced (severity=error — these fail the build):**
- No unused imports, no redundant imports, no illegal imports
- No tab characters
- One statement per line; `default` case last; uppercase `L` literals
- Lowercase package names; type parameters match `^[A-Z][a-zA-Z0-9]*$`
- No padding before a method's parameter list or inside cast parentheses

**Staged (severity=warning — reported, but do NOT fail the build yet):**
The rules below are real project conventions that the existing code does not yet
satisfy, so they are staged rather than enforced. Follow them in new code; you
just will not be blocked if something slips.
- No star imports (125 existing violations)
- Constants `UPPER_SNAKE_CASE`, exception: logger field `log` (170)
- K&R brace style (108 across `LeftCurly`/`RightCurly`)
- No `.toUpperCase()` / `.toLowerCase()` without a `Locale` argument (134)
- 4-space indentation (467)
- Complexity/length limits: method length 150, params 13, cyclomatic 19

To promote a staged rule, drive its count to zero, then add
`<property name="severity" value="error"/>` to that rule in
`.checkstyle/checkstyle.xml`. Staged rules carry no severity property of their
own — they inherit the Checker-level default of `warning`.

**Not checked at all:** per-file Apache license headers. Only 143 of 2168 Java
files carry one and most new files do not — the repository-root `LICENSE`
governs. Do not add headers to existing files as drive-by changes.

Other conventions:
- Lombok used in DTO/model classes (not universally)
- Run checkstyle before committing: `./mvnw checkstyle:check -pl <module>`
- Staged-tier warnings are not printed by default, to keep CI logs readable.
  To review them locally, add `-Dcheckstyle.consoleOutput=true`. Every
  violation is also written to `target/checkstyle-result.xml` either way.

### REST API
- Versioned at `/apis/registry/v3/`
- Implementation: `app/src/.../rest/v3/impl/`
- Response DTOs shared with Java SDK
- Never expose stack traces or internal errors to API clients

### Testing

See [DEVELOPING.md](DEVELOPING.md) for test commands and [CONTRIBUTING.md](CONTRIBUTING.md) for
storage-variant testing requirements. Storage-touching features must work across all variants.

## Contributor Checklist (external contributors)

Before opening a PR, verify every item. PRs that skip these get sent back.
Project committers have more latitude but should still follow the Code and Tests sections.
Full contribution guidelines are in [CONTRIBUTING.md](CONTRIBUTING.md).

### Before writing code
- [ ] **One PR at a time.** Do not open a second PR until your first one is merged. Maintainers will close additional PRs with "one PR at a time" — no exceptions, even if the work is ready.
- [ ] The linked issue has **maintainer approval** (a comment from a project maintainer). Implementing an unapproved feature request wastes everyone's time. Issues with zero maintainer comments are not approved.
- [ ] Check for **overlapping PRs** — search open PRs for your issue number and keywords. Duplicate work gets the later PR closed.
- [ ] Check the [Tried & Rejected list](https://github.com/Apicurio/apicurio-registry/discussions/8364) — some optimizations have already been evaluated and rejected with evidence. Don't re-implement them.

### Code
- [ ] Config properties follow `.claude/rules/config-properties.md` (`apicurio.*` prefix, `@Info` in `app` module). If you changed `@ConfigProperty` or `@Info`, regenerate config docs: `./mvnw clean install -pl :apicurio-registry-config-generator -am -DskipTests` and commit the updated `ref-registry-all-configs.adoc`.
- [ ] API error responses never expose internal state (usernames, stack traces, class names).
- [ ] Use Quarkus/MicroProfile facilities (`@CircuitBreaker`, `@Retry`, `@Timeout`) instead of hand-rolled equivalents.
- [ ] Use `Locale.ROOT` with `toUpperCase()` / `toLowerCase()`.
- [ ] Non-variant-specific changes under `storage/impl/` must work across all 4 storage variants.
- [ ] Auth changes require both positive and negative (403) test cases.
- [ ] New Java files follow the surrounding style. Do **not** add a per-file Apache
      license header — the project does not use them (see Code Style above).
- [ ] **No LLM artifacts** — fully qualified names must be imports (not inline `java.util.concurrent.TimeoutException`), annotations must be real (`@Tag`, `@Test`) not file paths, no hallucinated API parameters or system properties.
- [ ] **Input validation on endpoints** — validate path parameters against traversal, verify proxy/forwarding URLs are within expected domain, enforce request body size limits on endpoints accepting user content.
- [ ] **Default value consistency** — `@ConfigProperty(defaultValue=)`, activation conditions (`orElse()`), and `@Info` descriptions must all agree. A mismatch means one path sees a different default than the others.
- [ ] **Don't assume APIs exist** — before proposing a system property, annotation parameter, or config mechanism, verify it actually works by checking the library source. Hallucinated flags (e.g., `-Dawaitility.defaultTimeout`) waste review cycles.
- [ ] **No redundant guards** — don't add null checks for methods that already handle null (e.g., `Boolean.parseBoolean(null)` returns `false`). Don't call the same method twice when caching the result suffices.
- [ ] Don't change default config values unless that is the explicit goal of the PR.
- [ ] Use Fabric8 Kubernetes client API idiomatically (`ex.getStatus().getReason()`, not `ex.getMessage().contains(...)`).
- [ ] No `synchronized` in reactive/async code paths (`Uni<>`, Mutiny) — use `AtomicReference` + CAS or framework-provided mechanisms.

### Tests
- [ ] Every new code path has tests. Missing tests = automatic rejection.
- [ ] Test assertions check **specific values** ("counter is 3"), not just existence ("counter is not null").
- [ ] Security tests cover: authorized access, unauthorized access (403), edge cases (null tokens, expired sessions).
- [ ] Tests for CDI annotations (`@Retry`, `@CircuitBreaker`, `@Timeout`) use `@QuarkusTest` with injected beans — plain JUnit with `new` bypasses interceptors.
- [ ] If CI fails on a test unrelated to your change, report it as a separate issue with the flaky test class, error message, and CI run link.

### Submission
- [ ] `./mvnw test-compile -pl <module> -am -DskipTests` compiles cleanly (use `test-compile`, not `compile`, when touching test files).
- [ ] `./mvnw checkstyle:check -pl <module>` passes.
- [ ] All commits have DCO sign-off (`Signed-off-by: Name <email>`).
- [ ] Commit messages use Conventional Commits: `type(scope): description`.
- [ ] PR contains no unrelated changes (no whitespace fixes, no import reordering in untouched files).
- [ ] PR description explains **what** and **why**, not just "fixes #NNN".

## Watch Out For

- Protobuf-generated classes live in `target/` — don't edit them
- Storage implementations must stay in sync across variants
- UI has its own npm/Vite build system, separate from Maven
- Integration tests need running infrastructure (use testcontainers or profiles)
- `APICURIO_STORAGE_SQL_KIND` selects the SQL dialect (postgresql, mysql, mssql)
- New components must be wired into the Verify → Decide → Verification Gate CI pipeline, not standalone workflows. A standalone workflow that doesn't block merges is an incomplete integration.

## Claude Code Configuration

- **Commands**: See `.claude/commands/` for project-specific slash commands
- **Rules**: See `.claude/rules/` for path-scoped coding conventions
- **Agents**: See `.claude/agents/` for specialized subagent personas (`contributor-guide`, `code-reviewer`, `security-auditor`, `ci-debugger`)
- **Skills**: See `.claude/skills/` for auto-invoked workflow guides
- **Permissions**: See `.claude/settings.json` for team-shared permission policies
- **Hooks**: File protection and checkstyle-before-commit enabled by default; see `.claude/hooks/`
- **Personal overrides**: Create `CLAUDE.local.md` (gitignored) for your preferences
- **Optional**: Desktop notifications — add to your `settings.local.json`:
  ```json
  { "hooks": { "Notification": [{ "matcher": "", "hooks": [{ "type": "command", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/notify.sh" }] }] } }
  ```

## MCP Integration

The `mcp/` module provides an MCP server. Connect Claude Code to a running registry
instance for live artifact and schema management during development.
