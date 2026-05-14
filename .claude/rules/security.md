---
paths:
  - "app/src/main/java/**/auth/**/*.java"
  - "app/src/main/resources/application.properties"
---
# Security Considerations

- OIDC authentication via Keycloak (Quarkus OIDC extension)
- Auth configuration in `app/src/main/resources/application.properties`
- Never log secrets, tokens, or credentials
- Never commit `.env` files or credentials
- Integration tests with auth use Keycloak testcontainers (`dasniko/testcontainers-keycloak`)
- Role-based access control: `sr-admin`, `sr-developer`, `sr-readonly`
- TLS configuration handled via Quarkus config properties
- Secrets must use `SecretConfigSourceInterceptor`, not file-based sources
