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
- Apache 2.0 license header required on all Java files
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

## Watch Out For

- Protobuf-generated classes live in `target/` — don't edit them
- Storage implementations must stay in sync across variants
- UI has its own npm/Vite build system, separate from Maven
- Integration tests need running infrastructure (use testcontainers or profiles)
- `APICURIO_STORAGE_SQL_KIND` selects the SQL dialect (postgresql, mysql, mssql)

## Claude Code Configuration

- **Commands**: See `.claude/commands/` for project-specific slash commands
- **Rules**: See `.claude/rules/` for path-scoped coding conventions
- **Agents**: See `.claude/agents/` for specialized subagent personas
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
