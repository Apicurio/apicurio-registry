---
paths:
  - "app/src/**/*.java"
  - "common/src/**/*.java"
  - "schema-util/**/src/**/*.java"
  - "serdes/**/src/**/*.java"
  - "utils/**/src/**/*.java"
---
# Java Code Style

Follow `.checkstyle/checkstyle.xml`. It is tiered — only the enforced tier fails
the build, the rest are warnings being driven to zero. Write new code to satisfy
both tiers.

Enforced (build fails):
- No unused, redundant, or illegal imports
- No tab characters
- One statement per line; `default` case last; uppercase `L` in long literals
- Lowercase package names; type parameters match `^[A-Z][a-zA-Z0-9]*$`
- No padding before a parameter list or inside cast parentheses

Staged (warning only — still follow them):
- No star imports — use explicit imports
- Constants: `UPPER_SNAKE_CASE` (exception: logger field named `log`)
- Left/right curly braces on same line (K&R style)
- Never use `.toUpperCase()` or `.toLowerCase()` without a `Locale` argument
- 4-space indentation
- Max method length: 150 lines
- Max parameters: 13
- Cyclomatic complexity limit: 19

Not checked — do not add per-file Apache license headers; the project does not
use them.

Other:
- Use Lombok (`@Data`, `@Getter`, `@Builder`) only where the module already uses it
- Never expose stack traces to API clients
