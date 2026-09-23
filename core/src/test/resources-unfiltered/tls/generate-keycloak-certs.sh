#!/bin/bash
set -e

# Script to generate Keycloak TLS certificates and create a combined truststore
# that contains both Registry and Keycloak CA certificates

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PASSWORD="registrytest"
VALIDITY=3650  # 10 years

echo "Generating Keycloak server certificate..."

# Generate Keycloak keystore with self-signed certificate
keytool -genkeypair \
    -alias keycloak \
    -keyalg RSA \
    -keysize 2048 \
    -validity $VALIDITY \
    -keystore keycloak-keystore.jks \
    -storepass $PASSWORD \
    -keypass $PASSWORD \
    -dname "CN=localhost, OU=Apicurio, O=Red Hat, L=Raleigh, ST=NC, C=US" \
    -ext "SAN=dns:localhost,ip:127.0.0.1"

# Export Keycloak certificate to PEM format
keytool -exportcert \
    -alias keycloak \
    -keystore keycloak-keystore.jks \
    -storepass $PASSWORD \
    -rfc \
    -file keycloak-cert.pem

echo "Creating combined truststore with Registry and Keycloak certificates..."

# Create a temporary truststore and import both certificates
rm -f combined-truststore.jks combined-truststore.p12

# Import Registry certificate
keytool -importcert \
    -alias registry \
    -file registry-cert.pem \
    -keystore combined-truststore.jks \
    -storepass $PASSWORD \
    -noprompt

# Import Keycloak certificate
keytool -importcert \
    -alias keycloak \
    -file keycloak-cert.pem \
    -keystore combined-truststore.jks \
    -storepass $PASSWORD \
    -noprompt

# Also create PKCS12 version of combined truststore
keytool -importkeystore \
    -srckeystore combined-truststore.jks \
    -srcstorepass $PASSWORD \
    -destkeystore combined-truststore.p12 \
    -deststoretype PKCS12 \
    -deststorepass $PASSWORD

echo "Certificate generation complete!"
echo ""
echo "Generated files:"
echo "  - keycloak-keystore.jks    : Keycloak server keystore"
echo "  - keycloak-cert.pem        : Keycloak server certificate (PEM)"
echo "  - combined-truststore.jks  : Combined truststore (Registry + Keycloak CAs)"
echo "  - combined-truststore.p12  : Combined truststore (PKCS12 format)"
echo ""
echo "Verifying combined truststore contents..."
keytool -list -keystore combined-truststore.jks -storepass $PASSWORD
