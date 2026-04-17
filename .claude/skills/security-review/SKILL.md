---
name: security-review
description: Security review checklist. Use when the conversation involves
  authentication, authorization, OIDC, secrets, or TLS configuration changes.
allowed-tools: Read, Grep, Glob
---
# Security Review Checklist

When reviewing security-related changes:

1. **Secrets**: Ensure no credentials, tokens, or secrets are hardcoded or logged
2. **Auth configuration**: Verify OIDC/Keycloak config changes are backward-compatible
3. **Access control**: Check role-based access (`sr-admin`, `sr-developer`, `sr-readonly`) is enforced
4. **Error handling**: Confirm no stack traces or internal details leak to API responses
5. **Dependencies**: Flag any new dependencies that handle crypto or auth
6. **TLS**: Verify TLS configuration uses Quarkus config properties, not custom code
7. **Tests**: Ensure auth-related changes have integration tests with Keycloak testcontainer
