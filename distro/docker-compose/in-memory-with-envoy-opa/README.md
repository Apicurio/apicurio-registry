# Apicurio Registry with Externalized Authentication and Authorization

This is a proof of concept (POC) demonstrating externalized authentication and authorization for Apicurio
Registry using Envoy Proxy and Open Policy Agent (OPA).

## Architecture Overview

```
┌─────────┐
│ Client  │
└────┬────┘
     │ Authorization: Bearer <JWT>
     ▼
┌──────────────────────────────────────────┐
│          Envoy Proxy (PEP)               │
│  ┌────────────────────────────────────┐  │
│  │ 1. JWT Validation (Keycloak JWKS)  │  │
│  │    & Extract Claims to Headers     │  │
│  │    - X-Forwarded-User              │  │
│  │    - X-Forwarded-Email             │  │
│  │    - X-Forwarded-Groups            │  │
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │ 2. Authorization Check (OPA)       │  │
│  │    - Evaluate user roles           │  │
│  │    - Return allow/deny decision    │  │
│  └────────────────────────────────────┘  │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│      Apicurio Registry                   │
│  - No OIDC authentication                │
│  - Trusts identity headers from Envoy    │
│  - Business logic only                   │
└──────────────────────────────────────────┘

┌──────────────┐    ┌──────────────┐
│  Keycloak    │    │     OPA      │
│  (IdP)       │    │    (PDP)     │
│  - Users     │    │  - Policies  │
│  - Roles     │    │  - RBAC      │
│  - JWT       │    │              │
└──────────────┘    └──────────────┘
```

## Components

| Component           | Role                                    | Port  | Access        |
|---------------------|-----------------------------------------|-------|---------------|
| Keycloak            | Identity Provider (IdP)                 | 8080  | Public        |
| Envoy Proxy         | Policy Enforcement Point (PEP)          | 8081  | Public Entry  |
| OPA                 | Policy Decision Point (PDP)             | 8181  | Internal      |
| Apicurio Registry   | Business Logic                          | 8080  | Internal Only |
| Apicurio UI         | Web Interface                           | 8888  | Public        |

## Request Flow

1. **Client Authentication**: Client authenticates with Keycloak and receives a JWT token
2. **Request with JWT**: Client sends request to Apicurio Registry via Envoy with `Authorization: Bearer <JWT>`
   header
3. **JWT Validation & Header Injection**: Envoy validates the JWT (issuer, audience, signature, expiry) against
   Keycloak's JWKS endpoint and extracts claims to inject as headers:
   - `preferred_username` → X-Forwarded-User
   - `email` → X-Forwarded-Email
   - `realm_access.roles` → X-Forwarded-Groups (comma-separated)
4. **Authorization Query**: Envoy sends an authorization request to OPA with:
   - HTTP method (GET, POST, PUT, DELETE)
   - Request path
   - JWT payload (in metadata)
5. **Policy Evaluation**: OPA evaluates policies and returns allow/deny decision based on:
   - User roles (sr-admin, sr-developer, sr-readonly)
   - HTTP method
6. **Enforcement**:
   - If denied: Envoy rejects the request (403 Forbidden)
   - If allowed: Envoy forwards the request to Apicurio Registry with identity headers
7. **Registry Processing**: Apicurio Registry trusts the identity headers and processes the request

## Security Model

### Roles and Permissions

| Role         | Read | Write | Delete | Admin Operations |
|--------------|------|-------|--------|------------------|
| sr-admin     | ✓    | ✓     | ✓      | ✓                |
| sr-developer | ✓    | ✓     | ✓      | ✗                |
| sr-readonly  | ✓    | ✗     | ✗      | ✗                |

### Authorization Policy

The OPA policy (`policy.rego`) implements the following rules:

1. **Default Deny**: All requests are denied unless explicitly allowed
2. **Admin Access**: Users with `sr-admin` role can perform any operation
3. **Developer Access**: Users with `sr-developer` role can read and write
4. **Read-Only Access**: Users with `sr-readonly` role can only GET (read)

### Security Considerations

**CRITICAL REQUIREMENTS**:

1. **Network Isolation**: Apicurio Registry must NOT be directly accessible from outside the network.
   Only Envoy should be able to reach it.
2. **Header Stripping**: Envoy must strip all X-Forwarded-* headers from client requests before processing
3. **Trusted Proxy**: Apicurio Registry is configured to trust headers only from Envoy
4. **JWT Validation**: Envoy validates all JWTs before forwarding requests
5. **Policy Enforcement**: OPA must be consulted for every request

## Setup Instructions

### Prerequisites

- Docker and Docker Compose
- Port availability: 8080 (Keycloak), 8081 (Envoy), 8181 (OPA), 8888 (UI)

### Starting the Environment

1. Navigate to this directory:
   ```bash
   cd distro/docker-compose/in-memory-with-envoy-opa
   ```

2. Start all services:
   ```bash
   docker-compose up -d
   ```

3. Wait for all services to be healthy (approximately 30-60 seconds):
   ```bash
   docker-compose ps
   ```

4. Verify Keycloak is ready:
   ```bash
   curl http://localhost:8080/realms/registry
   ```

### Accessing the Services

- **Keycloak Admin Console**: http://localhost:8080/admin
  - Username: `admin`
  - Password: `admin`
- **Apicurio Registry API** (via Envoy): http://localhost:8081/apis/registry/v3
- **Apicurio Registry UI**: http://localhost:8888
- **OPA**: http://localhost:8181/v1/data/envoy/authz/allow (internal use)
- **Envoy Admin**: http://localhost:9901 (metrics and stats)

### Test Users

The Keycloak realm includes pre-configured test users:

| Username   | Password   | Role         | Permissions      |
|------------|------------|--------------|------------------|
| admin      | admin      | sr-admin     | Full access      |
| developer  | developer  | sr-developer | Read + Write     |
| developer2 | developer  | sr-developer | Read + Write     |
| user       | user       | sr-readonly  | Read only        |

## Testing

### 1. Obtain JWT Token

Get a token for the admin user:

```bash
export ADMIN_TOKEN=$(curl -X POST \
  http://localhost:8080/realms/registry/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=apicurio-registry" \
  -d "username=admin" \
  -d "password=admin" \
  | jq -r '.access_token')
```

Get a token for the readonly user:

```bash
export USER_TOKEN=$(curl -X POST \
  http://localhost:8080/realms/registry/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=apicurio-registry" \
  -d "username=user" \
  -d "password=user" \
  | jq -r '.access_token')
```

### 2. Test Read Access (Should Work for All Roles)

```bash
# As admin (should work)
curl -i http://localhost:8081/apis/registry/v3/groups \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# As readonly user (should work)
curl -i http://localhost:8081/apis/registry/v3/groups \
  -H "Authorization: Bearer $USER_TOKEN"
```

Expected: HTTP 200 OK

### 3. Test Write Access (Admin and Developer Only)

Create a group as admin:

```bash
curl -i -X POST http://localhost:8081/apis/registry/v3/groups \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "groupId": "test-group",
    "description": "Test group created by admin"
  }'
```

Expected: HTTP 200 OK

Create a group as readonly user (should fail):

```bash
curl -i -X POST http://localhost:8081/apis/registry/v3/groups \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "groupId": "test-group-2",
    "description": "This should fail"
  }'
```

Expected: HTTP 403 Forbidden

### 4. Test Unauthenticated Access (Should Fail)

```bash
curl -i http://localhost:8081/apis/registry/v3/groups
```

Expected: HTTP 401 Unauthorized

### 5. Verify Identity Headers

Check that Apicurio Registry receives the correct identity headers by examining the logs:

```bash
docker-compose logs apicurio-registry | grep "X-Forwarded"
```

### 6. Test Direct Access to Registry (Should Fail in Production)

**Note**: In this Docker Compose setup, direct access is possible for testing purposes. In production, network
isolation must prevent direct access.

```bash
# This bypasses Envoy - would fail with proper network isolation
curl -i http://localhost:8082/apis/registry/v3/groups
```

## Automated Test Script

Run the included test script:

```bash
chmod +x test.sh
./test.sh
```

This script tests all scenarios automatically and reports results.

## Troubleshooting

### JWT Validation Failures

Check Envoy logs:
```bash
docker-compose logs envoy
```

Common issues:
- Token expired (tokens expire after 5 minutes by default)
- Wrong audience in token
- JWKS endpoint unreachable

### Authorization Failures

Check OPA logs:
```bash
docker-compose logs opa
```

Test the OPA policy directly:
```bash
curl -X POST http://localhost:8181/v1/data/envoy/authz/allow \
  -H "Content-Type: application/json" \
  -d @test-input.json
```

### Registry Not Receiving Headers

Check that:
1. Envoy is injecting headers (check Envoy logs with debug level)
2. Registry is configured to trust proxy headers
3. Network connectivity between Envoy and Registry is working

## Customization

### Modifying Authorization Policies

Edit `policy.rego` to customize authorization logic. After changes:

```bash
docker-compose restart opa
```

### Adding Custom Roles

1. Add roles in Keycloak Admin Console
2. Assign roles to users
3. Update `policy.rego` to handle new roles
4. Restart OPA

### Changing Identity Headers

Edit `envoy.yaml` in the Lua filter section to change header names or add additional headers from JWT claims.

## Production Deployment Considerations

1. **Network Isolation**: Deploy Apicurio Registry in a private network, only accessible from Envoy
2. **TLS/HTTPS**: Enable TLS for all external communication
3. **Policy Management**: Use GitOps to manage OPA policies
4. **Monitoring**: Add observability (Prometheus metrics, distributed tracing)
5. **High Availability**: Run multiple instances of each component
6. **Secret Management**: Use secret management tools for credentials
7. **Token Refresh**: Implement token refresh logic in clients
8. **Rate Limiting**: Add rate limiting in Envoy
9. **Audit Logging**: Enable audit logging in OPA and Envoy

## Migration from Built-in Auth

To migrate from Apicurio's built-in authentication:

1. **Phase 1 - Parallel Run**: Run both auth systems in parallel for testing
2. **Phase 2 - Cutover**: Route production traffic through Envoy
3. **Phase 3 - Decommission**: Disable built-in auth in Apicurio Registry

## References

- [Envoy JWT Authentication](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/jwt_authn_filter)
- [Envoy External Authorization](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/ext_authz_filter)
- [OPA Documentation](https://www.openpolicyagent.org/docs/latest/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)

## Stopping the Environment

```bash
docker-compose down
```

To remove all data:

```bash
docker-compose down -v
```
