---
paths:
  - "app/src/**/*.java"
  - "common/src/**/*.java"
  - "schema-util/**/src/**/*.java"
  - "serdes/**/src/**/*.java"
  - "utils/**/src/**/*.java"
---
# Java Code Style

- Follow `.checkstyle/checkstyle.xml` strictly
- Apache 2.0 license header required (see `.checkstyle/java.header`)
- No star imports — use explicit imports
- No unused or redundant imports
- Constants: `UPPER_SNAKE_CASE` (exception: logger field named `log`)
- Left/right curly braces on same line (K&R style)
- Never use `.toUpperCase()` or `.toLowerCase()` without a `Locale` argument
- Max method length: 150 lines
- Max parameters: 13
- Cyclomatic complexity limit: 19
- Use Lombok (`@Data`, `@Getter`, `@Builder`) only where the module already uses it
- Never expose stack traces to API clients
