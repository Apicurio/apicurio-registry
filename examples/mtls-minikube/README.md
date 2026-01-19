# Apicurio Registry mTLS Example for Minikube

This example demonstrates how to deploy and test Apicurio Registry with mutual TLS (mTLS) authentication on Minikube.

## Overview

Mutual TLS (mTLS) is a security protocol that provides two-way authentication between client and server. Unlike standard TLS where only the server is authenticated, mTLS requires both parties to present certificates:

- **Server authentication**: The client verifies the server's certificate (standard TLS)
- **Client authentication**: The server verifies the client's certificate (mutual authentication)

This example includes:
1. Scripts to generate CA, server, and client certificates
2. Kubernetes deployment files for deploying to Minikube
3. A Java client demonstrating mTLS connectivity

## Prerequisites

- **Minikube**: Running Kubernetes cluster ([Installation guide](https://minikube.sigs.k8s.io/docs/start/))
- **kubectl**: Kubernetes command-line tool
- **Java 17+**: For running the client example
- **Maven**: For building the client example
- **OpenSSL**: For certificate generation
- **keytool**: Part of the JDK, for managing keystores

## Quick Start

### 1. Start Minikube

```bash
minikube start
```

### 2. Install Apicurio Registry Operator

```bash
kubectl apply -f https://github.com/Apicurio/apicurio-registry-operator/releases/latest/download/install.yaml
```

Wait for the operator to be ready:

```bash
kubectl wait --for=condition=ready pod -l name=apicurio-registry-operator -n apicurio-registry-operator-namespace --timeout=300s
```

### 3. Deploy the Registry with mTLS

The deployment script automates certificate generation and deployment:

```bash
./deploy.sh
```

This script will:
1. Check if Minikube is running
2. Generate certificates (if not already present)
3. Create the namespace
4. Create Kubernetes secrets with certificates
5. Deploy Apicurio Registry with mTLS configuration
6. Wait for the deployment to be ready

### 4. Set up Port Forwarding

In a separate terminal, forward the registry port to localhost:

```bash
kubectl port-forward -n apicurio-mtls svc/apicurio-registry-mtls-app-service 8443:8443
```

### 5. Run the Client Demo

```bash
cd client
mvn clean compile exec:java
```

The client will:
- Connect to the registry using mTLS
- Verify the connection by retrieving system info
- Create a sample JSON Schema artifact
- Retrieve the artifact
- Delete the artifact

## Manual Deployment Steps

If you prefer to deploy manually:

### Step 1: Generate Certificates

```bash
cd certs
./generate-certs.sh
cd ..
```

This generates:
- CA certificate and key
- Server certificate, keystore, and truststore
- Client certificate, keystore, and truststore

All keystores use the password: `apicurio`

### Step 2: Create Kubernetes Resources

```bash
# Create namespace
kubectl apply -f k8s/namespace.yaml

# Create secrets from certificates
./k8s/create-secrets.sh

# Deploy registry
kubectl apply -f k8s/apicurio-registry-mtls.yaml
```

### Step 3: Verify Deployment

```bash
# Check pod status
kubectl get pods -n apicurio-mtls

# View logs
kubectl logs -n apicurio-mtls -l app.kubernetes.io/name=apicurio-registry-mtls -f
```

## How It Works

### Server Configuration

The registry is configured with the following Quarkus properties (via environment variables):

```yaml
QUARKUS_HTTP_SSL_CLIENT_AUTH: "required"  # Require client certificates
QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_FILE: "/deployments/certs/server-keystore.p12"
QUARKUS_HTTP_SSL_CERTIFICATE_TRUST_STORE_FILE: "/deployments/certs/server-truststore.p12"
QUARKUS_HTTP_INSECURE_REQUESTS: "disabled"  # Force HTTPS only
```

### Client Configuration

The Java client uses the SDK's built-in support for mTLS:

```java
RegistryClientOptions options = RegistryClientOptions.create(registryUrl)
    .keystorePkcs12(keystorePath, password)      // Client certificate
    .trustStorePkcs12(truststorePath, password); // Trust server CA
```

### Certificate Chain

```
CA Certificate (ca-cert.pem)
├── Server Certificate (server-cert.pem)
│   └── Used by: Apicurio Registry
└── Client Certificate (client-cert.pem)
    └── Used by: Client applications
```

## Testing with curl

You can also test mTLS using curl:

```bash
# Convert PKCS12 to PEM format for curl
cd certs
openssl pkcs12 -in client-keystore.p12 -out client-cert-and-key.pem -nodes -passin pass:apicurio

# Test connection
curl -v --cert client-cert-and-key.pem \
     --cacert ca-cert.pem \
     https://localhost:8443/apis/registry/v3/system/info
```

## Troubleshooting

### Connection Refused

**Problem**: Cannot connect to `https://localhost:8443`

**Solution**: Ensure port forwarding is active:
```bash
kubectl port-forward -n apicurio-mtls svc/apicurio-registry-mtls-app-service 8443:8443
```

### Certificate Errors

**Problem**: `javax.net.ssl.SSLHandshakeException` or certificate validation errors

**Solutions**:
1. Regenerate certificates:
   ```bash
   cd certs
   rm -f *.jks *.p12 *.pem
   ./generate-certs.sh
   ```

2. Recreate secrets:
   ```bash
   ./k8s/create-secrets.sh
   ```

3. Restart the registry pod:
   ```bash
   kubectl rollout restart deployment -n apicurio-mtls
   ```

### Pod Not Starting

**Problem**: Registry pod is in CrashLoopBackOff or Error state

**Solutions**:
1. Check logs:
   ```bash
   kubectl logs -n apicurio-mtls -l app.kubernetes.io/name=apicurio-registry-mtls
   ```

2. Verify secrets exist:
   ```bash
   kubectl get secrets -n apicurio-mtls
   ```

3. Check operator status:
   ```bash
   kubectl get pods -n apicurio-registry-operator-namespace
   ```

### Client Connection Fails

**Problem**: Client cannot connect even with certificates

**Solutions**:
1. Verify certificates were generated:
   ```bash
   ls -la certs/*.p12
   ```

2. Check certificate validity:
   ```bash
   keytool -list -v -keystore certs/client-keystore.p12 -storepass apicurio
   ```

3. Ensure registry is using HTTPS:
   ```bash
   kubectl get svc -n apicurio-mtls
   ```

## Cleanup

To remove all resources:

```bash
# Delete the namespace (removes all resources)
kubectl delete namespace apicurio-mtls

# Optional: Remove generated certificates
rm -rf certs/*.jks certs/*.p12 certs/*.pem certs/*.crt certs/*.key
```

## Configuration Options

### Environment Variables for Client

The client demo supports the following environment variables:

- `REGISTRY_URL`: Registry endpoint (default: `https://localhost:8443/apis/registry/v3`)
- `CERTS_DIR`: Directory containing certificates (default: `../certs`)
- `KEYSTORE_PASSWORD`: Password for keystores (default: `apicurio`)

Example:
```bash
export REGISTRY_URL=https://my-registry:8443/apis/registry/v3
export CERTS_DIR=/path/to/certs
mvn clean compile exec:java
```

### Customizing Certificate Settings

Edit `certs/generate-certs.sh` to customize:
- Validity period (default: 365 days)
- Subject Alternative Names (SANs)
- Certificate subjects
- Key sizes

## Production Considerations

This example is intended for **development and testing only**. For production use:

1. **Use a proper CA**: Don't use self-signed certificates
2. **Secure certificate storage**: Use Kubernetes secrets with encryption at rest
3. **Certificate rotation**: Implement automated certificate renewal
4. **Storage backend**: Use PostgreSQL or KafkaSQL instead of in-memory storage
5. **Network policies**: Restrict network access with Kubernetes NetworkPolicies
6. **Resource limits**: Set appropriate CPU and memory limits
7. **High availability**: Deploy multiple replicas with proper storage

## Related Documentation

- [Quarkus Mutual TLS Guide](https://quarkus.io/blog/quarkus-mutual-tls/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Apicurio Registry Operator](https://github.com/Apicurio/apicurio-registry-operator)

## Issue Reference

This example addresses GitHub issue: [#914 - Test/verify an mTLS configuration](https://github.com/Apicurio/apicurio-registry/issues/914)
