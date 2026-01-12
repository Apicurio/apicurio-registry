# Maven Plugin TLS and Credentials Example

This example demonstrates how to configure TLS (Transport Layer Security) and authentication credentials when using the Apicurio Registry Maven plugin.

## Overview

When connecting to a TLS-enabled Apicurio Registry instance, you need to configure the Maven plugin to:

1. **Trust the server's certificate** - Configure a trust store containing the CA certificate(s) that signed the registry's server certificate
2. **Authenticate to the registry** - Optionally configure authentication credentials (basic auth, OAuth2, or mTLS)

## Configuration Options

### TLS Trust Store Configuration

| Parameter | Description |
|-----------|-------------|
| `trustStorePath` | Path to the trust store file (JKS, PKCS12, or PEM format) |
| `trustStorePassword` | Password for the trust store (required for JKS and PKCS12) |
| `trustStoreType` | Optional: Explicit type (JKS, PKCS12, or PEM). Auto-detected from file extension if not specified |

### mTLS Key Store Configuration (for client certificate authentication)

| Parameter | Description |
|-----------|-------------|
| `keyStorePath` | Path to the key store file containing the client certificate |
| `keyStorePassword` | Password for the key store (required for JKS and PKCS12) |
| `keyStoreType` | Optional: Explicit type (JKS, PKCS12, or PEM) |
| `keyStorePemKeyPath` | Path to the PEM private key file (required when using PEM format) |

### Additional SSL Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `trustAll` | `false` | Trust all certificates without validation (development only) |
| `verifyHostname` | `true` | Verify server hostname matches certificate |

### Authentication Options

| Parameter | Description |
|-----------|-------------|
| `username` | Username for basic authentication |
| `password` | Password for basic authentication |
| `authServerUrl` | OAuth2/OIDC token endpoint URL |
| `clientId` | OAuth2 client ID |
| `clientSecret` | OAuth2 client secret |
| `clientScope` | OAuth2 scope (optional) |

## Examples

This project includes multiple Maven profiles demonstrating different TLS and authentication configurations:

### Example 1: Basic TLS with PKCS12 Trust Store

```xml
<configuration>
    <registryUrl>https://registry.example.com:8443/apis/registry/v3</registryUrl>
    <trustStorePath>${project.basedir}/certs/truststore.p12</trustStorePath>
    <trustStorePassword>${env.TRUSTSTORE_PASSWORD}</trustStorePassword>
    ...
</configuration>
```

### Example 2: TLS with Basic Authentication

```bash
mvn generate-sources -Ptls-basic-auth
```

```xml
<configuration>
    <registryUrl>https://registry.example.com:8443/apis/registry/v3</registryUrl>
    <trustStorePath>${project.basedir}/certs/truststore.p12</trustStorePath>
    <trustStorePassword>${env.TRUSTSTORE_PASSWORD}</trustStorePassword>
    <username>${env.REGISTRY_USERNAME}</username>
    <password>${env.REGISTRY_PASSWORD}</password>
    ...
</configuration>
```

### Example 3: TLS with OAuth2 Authentication

```bash
mvn generate-sources -Ptls-oauth2
```

```xml
<configuration>
    <registryUrl>https://registry.example.com:8443/apis/registry/v3</registryUrl>
    <trustStorePath>${project.basedir}/certs/truststore.p12</trustStorePath>
    <trustStorePassword>${env.TRUSTSTORE_PASSWORD}</trustStorePassword>
    <authServerUrl>https://keycloak.example.com/realms/registry/protocol/openid-connect/token</authServerUrl>
    <clientId>${env.CLIENT_ID}</clientId>
    <clientSecret>${env.CLIENT_SECRET}</clientSecret>
    ...
</configuration>
```

### Example 4: Mutual TLS (mTLS)

```bash
mvn generate-sources -Pmtls
```

```xml
<configuration>
    <registryUrl>https://registry.example.com:8443/apis/registry/v3</registryUrl>
    <!-- Trust store for server certificate verification -->
    <trustStorePath>${project.basedir}/certs/truststore.p12</trustStorePath>
    <trustStorePassword>${env.TRUSTSTORE_PASSWORD}</trustStorePassword>
    <!-- Key store for client certificate authentication -->
    <keyStorePath>${project.basedir}/certs/client-keystore.p12</keyStorePath>
    <keyStorePassword>${env.KEYSTORE_PASSWORD}</keyStorePassword>
    ...
</configuration>
```

### Example 5: TLS with PEM Certificates

```bash
mvn generate-sources -Ptls-pem
```

```xml
<configuration>
    <registryUrl>https://registry.example.com:8443/apis/registry/v3</registryUrl>
    <trustStorePath>${project.basedir}/certs/ca-cert.pem</trustStorePath>
    <trustStoreType>PEM</trustStoreType>
    ...
</configuration>
```

### Example 6: mTLS with PEM Certificates

```bash
mvn generate-sources -Pmtls-pem
```

```xml
<configuration>
    <registryUrl>https://registry.example.com:8443/apis/registry/v3</registryUrl>
    <trustStorePath>${project.basedir}/certs/ca-cert.pem</trustStorePath>
    <trustStoreType>PEM</trustStoreType>
    <keyStorePath>${project.basedir}/certs/client-cert.pem</keyStorePath>
    <keyStorePemKeyPath>${project.basedir}/certs/client-key.pem</keyStorePemKeyPath>
    <keyStoreType>PEM</keyStoreType>
    ...
</configuration>
```

### Example 7: TLS with JKS Trust Store

```bash
mvn generate-sources -Ptls-jks
```

```xml
<configuration>
    <registryUrl>https://registry.example.com:8443/apis/registry/v3</registryUrl>
    <trustStorePath>${project.basedir}/certs/truststore.jks</trustStorePath>
    <trustStorePassword>${env.TRUSTSTORE_PASSWORD}</trustStorePassword>
    ...
</configuration>
```

### Example 8: Development Mode (Trust All)

```bash
mvn generate-sources -Pdev-trust-all
```

**WARNING: This should ONLY be used for development/testing. Never use in production.**

```xml
<configuration>
    <registryUrl>https://localhost:8443/apis/registry/v3</registryUrl>
    <trustAll>true</trustAll>
    <verifyHostname>false</verifyHostname>
    ...
</configuration>
```

## Security Best Practices

1. **Never commit passwords or secrets** - Use environment variables or Maven property files excluded from version control
2. **Never use `trustAll=true` in production** - This disables certificate validation and makes connections vulnerable to MITM attacks
3. **Prefer PKCS12 over JKS** - PKCS12 is the industry standard and is more portable
4. **Keep private keys secure** - Restrict file permissions and use encrypted key stores
5. **Rotate certificates regularly** - Follow your organization's certificate lifecycle policies

## Creating Certificates for Testing

### Generate a self-signed CA and server certificate

```bash
# Create CA key and certificate
openssl genrsa -out ca-key.pem 4096
openssl req -new -x509 -key ca-key.pem -out ca-cert.pem -days 365 \
    -subj "/CN=Test CA"

# Create server key and certificate signed by CA
openssl genrsa -out server-key.pem 4096
openssl req -new -key server-key.pem -out server.csr \
    -subj "/CN=registry.example.com"
openssl x509 -req -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -days 365

# Create PKCS12 trust store
openssl pkcs12 -export -nokeys -in ca-cert.pem -out truststore.p12 \
    -passout pass:changeit
```

### Generate client certificate for mTLS

```bash
# Create client key and certificate
openssl genrsa -out client-key.pem 4096
openssl req -new -key client-key.pem -out client.csr \
    -subj "/CN=registry-client"
openssl x509 -req -in client.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out client-cert.pem -days 365

# Create PKCS12 client key store
openssl pkcs12 -export -in client-cert.pem -inkey client-key.pem \
    -out client-keystore.p12 -passout pass:changeit
```

## Running the Examples

1. Set up a TLS-enabled Apicurio Registry instance
2. Create appropriate certificates and trust stores
3. Set environment variables for passwords:
   ```bash
   export TRUSTSTORE_PASSWORD=changeit
   export KEYSTORE_PASSWORD=changeit
   export REGISTRY_USERNAME=admin
   export REGISTRY_PASSWORD=admin123
   export CLIENT_ID=registry-client
   export CLIENT_SECRET=client-secret
   ```
4. Run one of the profile examples:
   ```bash
   mvn generate-sources -Ptls-basic-auth
   ```

## Related Resources

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [mTLS Minikube Example](../mtls-minikube/) - Full mTLS setup with Kubernetes
- [Java SDK Documentation](../../java-sdk/) - Programmatic TLS configuration
