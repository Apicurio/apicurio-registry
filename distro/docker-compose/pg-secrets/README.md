# Apicurio Registry with PostgreSQL and File-Based Secrets

This example demonstrates how to use file-based secrets with Apicurio Registry and Docker Compose,
following Docker's recommended secret management practices.

## Overview

Instead of passing sensitive credentials as environment variables (which can be exposed in process
lists and container inspection), this example uses Docker Compose secrets to securely manage
database credentials.

## Features

- **Secure credential management**: Credentials are stored in files and mounted as Docker secrets
- **No environment variable exposure**: Sensitive data never appears in environment variables
- **Production-ready pattern**: Follows Docker Compose best practices for secret management
- **Works with external secret management**: Compatible with Docker Swarm secrets and Kubernetes
  secrets when migrating to orchestration platforms

## Prerequisites

- Docker and Docker Compose installed
- Basic understanding of Docker Compose

## Setup

### 1. Create Secret Files

First, create a directory for your secrets and add the credential files:

```bash
mkdir -p secrets
echo "pguser" > secrets/db_user.txt
echo "pgpass" > secrets/db_password.txt
echo "pguser" > secrets/registry_db_user.txt
echo "pgpass" > secrets/registry_db_password.txt
```

**Important**: In production, ensure proper file permissions:

```bash
chmod 600 secrets/*.txt
```

### 2. Start the Services

```bash
docker compose up -d
```

This will start:
- PostgreSQL database with file-based credentials
- Apicurio Registry configured to read database credentials from secret files
- Apicurio Registry UI

### 3. Verify the Deployment

Check that all services are running:

```bash
docker compose ps
```

Access the Apicurio Registry UI at: http://localhost:8888

Access the Apicurio Registry API at: http://localhost:8080

## How It Works

### File-Based Secret Configuration

Apicurio Registry supports file-based secrets for **any configuration property** by adding a `_FILE`
suffix to the environment variable name (or `.file` suffix to the property name).

**How it works**:
1. Add `_FILE` suffix to any environment variable (e.g., `APICURIO_DATASOURCE_PASSWORD_FILE`)
2. Set the value to a file path (e.g., `/run/secrets/db_password`)
3. Quarkus converts the environment variable to a property (e.g., `apicurio.datasource.password.file`)
4. FileBasedSecretsConfigSource reads the file contents and provides the actual value

**Common examples:**
- `APICURIO_DATASOURCE_USERNAME_FILE` → Database username from file
- `APICURIO_DATASOURCE_PASSWORD_FILE` → Database password from file
- `QUARKUS_OIDC_CLIENT_SECRET_FILE` → OIDC client secret from file
- `APICURIO_KAFKASQL_SECURITY_SASL_CLIENT_SECRET_FILE` → Kafka SASL secret from file

This works for **any** property, not just the examples above. Simply add `_FILE` to any environment
variable name and point it to a file containing the secret value.

### Docker Compose Secrets

Docker Compose mounts secrets as files in `/run/secrets/` inside the container:

```yaml
secrets:
  registry_db_password:
    file: ./secrets/registry_db_password.txt

services:
  apicurio-registry:
    environment:
      APICURIO_DATASOURCE_PASSWORD_FILE: /run/secrets/registry_db_password
    secrets:
      - registry_db_password
```

### Precedence

Configuration precedence in Apicurio Registry:
1. System properties (highest priority - 400)
2. File-based secrets via `_FILE` properties (priority 350)
3. Environment variables (priority 300)
4. application.properties (lowest priority - 250)

This means if both `APICURIO_DATASOURCE_PASSWORD_FILE` and `APICURIO_DATASOURCE_PASSWORD` are set,
the `_FILE` variant takes precedence and the credential will be read from the file.

## Supported File-Based Secrets

**All** configuration properties support file-based configuration by adding `_FILE` to the environment
variable name. Some common examples:

**Datasource credentials:**
- `APICURIO_DATASOURCE_USERNAME_FILE` / `APICURIO_DATASOURCE_PASSWORD_FILE`
- `APICURIO_DATASOURCE_BLUE_USERNAME_FILE` / `APICURIO_DATASOURCE_BLUE_PASSWORD_FILE`
- `APICURIO_DATASOURCE_GREEN_USERNAME_FILE` / `APICURIO_DATASOURCE_GREEN_PASSWORD_FILE`

**OAuth/OIDC credentials:**
- `QUARKUS_OIDC_CLIENT_SECRET_FILE`
- `APICURIO_UI_AUTH_OIDC_CLIENT_SECRET_FILE`

**Kafka SASL credentials:**
- `APICURIO_KAFKASQL_SECURITY_SASL_CLIENT_ID_FILE`
- `APICURIO_KAFKASQL_SECURITY_SASL_CLIENT_SECRET_FILE`

**Kafka SSL/TLS passwords:**
- `APICURIO_KAFKASQL_SECURITY_SSL_TRUSTSTORE_PASSWORD_FILE`
- `APICURIO_KAFKASQL_SECURITY_SSL_KEYSTORE_PASSWORD_FILE`
- `APICURIO_KAFKASQL_SECURITY_SSL_KEY_PASSWORD_FILE`

**Any other property:**
Simply add `_FILE` to any environment variable and point it to a file.

## File Format

Secret files should contain only the credential value:
- Plain text format (UTF-8 encoding)
- Leading and trailing whitespace is automatically trimmed
- No special formatting required

Example:
```
mypassword
```

or with trailing newline (automatically trimmed):
```
mypassword

```

## Production Considerations

### File Permissions

Ensure secret files have restrictive permissions:
```bash
chmod 600 secrets/*.txt
chown root:root secrets/*.txt  # In production
```

Apicurio Registry will log a warning if secret files are world-readable.

### External Secret Management

For production deployments, consider using:

- **Docker Swarm**: Use Docker Swarm secrets instead of file-based secrets
- **Kubernetes**: Mount secrets from Kubernetes Secret resources
- **HashiCorp Vault**: Use Vault to manage and inject secrets
- **Cloud Provider Secrets**: AWS Secrets Manager, Azure Key Vault, GCP Secret Manager

### Migration from Environment Variables

To migrate from environment variable-based credentials:

1. Create secret files with current credentials
2. Update docker-compose.yml to use `_FILE` environment variables
3. Test the configuration
4. Remove the old environment variables

Both approaches work simultaneously, with `_FILE` taking precedence.

## Cleanup

To stop and remove all services:

```bash
docker compose down -v
```

To also remove secret files (be careful in production):

```bash
rm -rf secrets/
```

## Troubleshooting

### Application fails to start with "Secret file not found"

Ensure the secret files exist and the paths are correct:
```bash
ls -la secrets/
```

### Application fails to start with "Secret file not readable"

Check file permissions:
```bash
chmod 600 secrets/*.txt
```

### Database connection fails

Verify that the username and password in the secret files match what PostgreSQL expects:
```bash
cat secrets/db_user.txt
cat secrets/registry_db_user.txt
```

## References

- [Docker Compose Secrets Documentation](https://docs.docker.com/compose/how-tos/use-secrets/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
