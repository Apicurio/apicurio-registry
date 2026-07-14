# AI Agent Configuration

This project provides agent definitions for AI coding tools (Claude Code, Copilot, Cursor, etc.) to enforce project-specific quality standards.

## For Contributors

**Before opening a PR**, run the contributor quality gate to catch issues maintainers will reject:

```
# Claude Code
/contributor-guide

# Or spawn it manually
Agent(subagent_type: "contributor-guide", prompt: "Review my changes against the Contributor Checklist in CLAUDE.md")
```

This checks: maintainer approval on the issue, config property naming, missing tests, security anti-patterns, storage variant coverage, and code style. See the [Contributor Checklist](CLAUDE.md#contributor-checklist) for the full list.

## Available Agents

| Agent | Purpose | When to use |
|-------|---------|-------------|
| [`contributor-guide`](.claude/agents/contributor-guide.md) | Pre-submission quality gate | Before opening any PR |
| [`code-reviewer`](.claude/agents/code-reviewer.md) | General code review | When reviewing PRs for correctness |
| [`security-auditor`](.claude/agents/security-auditor.md) | Security-focused review | When touching auth, authz, secrets, or TLS |
| [`ci-debugger`](.claude/agents/ci-debugger.md) | CI failure diagnosis | When a GitHub Actions workflow fails |

## Project Rules

Agent behavior is governed by:
- [`CLAUDE.md`](CLAUDE.md) — project architecture, conventions, and contributor checklist
- [`.claude/rules/`](.claude/rules/) — path-scoped coding conventions (config properties, code style, testing)
- [`.claude/settings.json`](.claude/settings.json) — tool permissions and hooks
