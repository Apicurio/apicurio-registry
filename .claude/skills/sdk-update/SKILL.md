---
name: sdk-update
description: Guide for updating SDKs when REST API changes are made. Use when
  the user modifies REST API endpoints, request/response models, or the OpenAPI spec.
allowed-tools: Read, Grep, Glob
---
# SDK Update Guide

When REST API changes are made:

1. **Regenerate OpenAPI spec** — the spec is auto-generated; verify it reflects the changes
2. **Java SDK** (`java-sdk/`, `java-sdk-v2/`, `java-sdk-common/`) — update models and client methods
3. **Go SDK** (`go-sdk/`) — regenerate from OpenAPI spec
4. **Python SDK** (`python-sdk/`) — regenerate from OpenAPI spec using Kiota
5. **TypeScript SDK** (`typescript-sdk/`) — regenerate from OpenAPI spec
6. **Update integration tests** that use SDK clients
7. **Check backward compatibility** — new fields should be optional, removed fields need migration
