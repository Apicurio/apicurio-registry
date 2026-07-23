# Apicurio Registry

Open-source API and Schema Registry. Apache 2.0 license, DCO sign-off required on all commits.

## Build & Test Commands

```bash
./mvnw clean install -DskipTests        # Full build, skip tests
./mvnw clean install                     # Full build with unit tests
./mvnw quarkus:dev                       # Dev mode (run from app/)
./mvnw test -pl <module>                 # Run tests for a specific module
./mvnw checkstyle:check -pl <module>     # Run checkstyle for a module
./mvnw verify -pl integration-tests -Plocal-tests  # Integration tests
```

UI (separate build system):
```bash
cd ui && npm install && npm run build    # Build UI
cd ui/ui-app && npm run dev              # UI dev server
```

## Architecture

Multi-module Maven project (~30 modules). Java 17 (source), Java 21 (runtime). Quarkus 3.27.2.

### Key Modules

| Module | Purpose |
|--------|---------|
| `app/` | Main Quarkus application (REST API, auth, storage orchestration) |
| `common/` | Shared models, interfaces, DTOs |
| `storage/` | Storage layer implementations (under `app/src/.../storage/impl/`) |
| `ui/` | React + TypeScript frontend (ui-app/, ui-docs/, ui-editors/) |
| `java-sdk/`, `go-sdk/`, `python-sdk/`, `typescript-sdk/` | Client SDKs |
| `serdes/` | Kafka/NATS/Pulsar serializers/deserializers |
| `schema-util/` | Schema type utilities (Avro, Protobuf, JSON Schema, OpenAPI, etc.) |
| `integration-tests/` | Cross-module integration test suite |
| `operator/` | Kubernetes operator |
| `mcp/` | MCP server (Model Context Protocol, uses quarkus-mcp-server-stdio) |
| `cli/` | Command-line interface |

### Storage Variants

Selected via `APICURIO_STORAGE_KIND` environment variable:

- **sql** (default) — PostgreSQL via JDBC. Canonical implementation.
- **kafkasql** — Kafka journal + SQL snapshot. State changes replicated via Kafka topics.
- **gitops** — Git repository as backing store. Read-only mode.
- **kubernetesops** — Kubernetes ConfigMaps as backing store.

Implementations: `app/src/main/java/io/apicurio/registry/storage/impl/`

## Conventions

### Commit Messages
Conventional Commits format: `<type>(<scope>): <description> (#PR)`

Types: `feat`, `fix`, `chore`, `docs`, `ci`, `test`, `refactor`

### Code Style
- Checkstyle config: `.checkstyle/checkstyle.xml`
- No star imports, no unused imports
- Constants: `UPPER_SNAKE_CASE` (exception: logger field `log`)
- K&R brace style (left curly on same line)
- No `.toUpperCase()` / `.toLowerCase()` without `Locale` argument
- Lombok used in DTO/model classes (not universally)
- Run checkstyle before committing: `./mvnw checkstyle:check -pl <module>`

### REST API
- Versioned at `/apis/registry/v3/`
- Implementation: `app/src/.../rest/v3/impl/`
- Response DTOs shared with Java SDK
- Never expose stack traces or internal errors to API clients

### Testing
- Unit tests: `@QuarkusTest` annotation, same module under `src/test/`
- Integration tests: `integration-tests/` module
- Profiles: `local-tests`, `remote-mem`, `remote-sql`, `remote-kafka`
- Storage-touching features must work across all variants

## Contributor Checklist

Before opening a PR, verify every item. PRs that skip these get sent back.

### Before writing code
- [ ] The linked issue has **maintainer approval** (a comment from a project maintainer). Implementing an unapproved feature request wastes everyone's time.
- [ ] Check for **overlapping PRs** — search open PRs for your issue number and keywords. Duplicate work gets the later PR closed.

### Code
- [ ] Config properties follow `.claude/rules/config-properties.md` (`apicurio.*` prefix, `@Info` annotation in `app` module, run config doc generator).
- [ ] API error responses never expose internal state — no usernames, stack traces, or class names. Use generic messages.
- [ ] Never hand-roll what Quarkus or MicroProfile already provides (circuit breakers, retry, fault tolerance, health checks). Check existing dependencies first.
- [ ] New features or behavioral changes under `storage/impl/` that aren't variant-specific must be implemented across all 4 storage variants.
- [ ] Auth changes require tests for both **positive** (authorized → succeeds) and **negative** (unauthorized → 403) cases.
- [ ] New Java files include the Apache 2.0 license header.
- [ ] **No LLM artifacts** — fully qualified names must be imports (not inline `java.util.concurrent.TimeoutException`), annotations must be real (`@Tag`, `@Test`) not file paths, no hallucinated API parameters or system properties.
- [ ] **Input validation on endpoints** — validate path parameters against traversal, verify proxy/forwarding URLs are within expected domain, enforce request body size limits on endpoints accepting user content.
- [ ] **Default value consistency** — `@ConfigProperty(defaultValue=)`, activation conditions (`orElse()`), and `@Info` descriptions must all agree. A mismatch means one path sees a different default than the others.
- [ ] **Don't assume APIs exist** — before proposing a system property, annotation parameter, or config mechanism, verify it actually works by checking the library source. Hallucinated flags (e.g., `-Dawaitility.defaultTimeout`) waste review cycles.
- [ ] **No redundant guards** — don't add null checks for methods that already handle null (e.g., `Boolean.parseBoolean(null)` returns `false`). Don't call the same method twice when caching the result suffices.

### Tests
- [ ] Every new code path has tests. Missing tests = automatic rejection.
- [ ] Test assertions check **specific values** ("counter is 3"), not just existence ("counter is not null").
- [ ] Security tests cover: authorized access, unauthorized access (403), edge cases (null tokens, expired sessions).
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
