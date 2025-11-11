#!/bin/bash

# Script to generate certificates for mutual TLS (mTLS) testing
# This script creates a CA, server certificates, and client certificates

set -e

CERTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDITY_DAYS=365
PASSWORD="apicurio"

echo "Generating certificates for mTLS..."
echo "Output directory: ${CERTS_DIR}"

# Clean up existing certificates
rm -f "${CERTS_DIR}"/*.jks "${CERTS_DIR}"/*.p12 "${CERTS_DIR}"/*.pem "${CERTS_DIR}"/*.crt "${CERTS_DIR}"/*.key "${CERTS_DIR}"/*.csr "${CERTS_DIR}"/*.srl

# 1. Generate CA (Certificate Authority) with proper CA extensions
echo "1. Generating CA..."

# Create CA config file with CA extensions
cat > "${CERTS_DIR}/ca-ext.cnf" << EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_ca
prompt = no

[req_distinguished_name]
CN = Apicurio Registry CA
O = Apicurio
C = US

[v3_ca]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical,CA:TRUE
keyUsage = critical,digitalSignature,keyCertSign,cRLSign
EOF

openssl genrsa -out "${CERTS_DIR}/ca-key.pem" 4096
openssl req -new -x509 -days ${VALIDITY_DAYS} -key "${CERTS_DIR}/ca-key.pem" \
    -out "${CERTS_DIR}/ca-cert.pem" \
    -config "${CERTS_DIR}/ca-ext.cnf"

# 2. Generate Server Certificate
echo "2. Generating Server Certificate..."
openssl genrsa -out "${CERTS_DIR}/server-key.pem" 4096
openssl req -new -key "${CERTS_DIR}/server-key.pem" \
    -out "${CERTS_DIR}/server.csr" \
    -subj "/CN=apicurio-registry/O=Apicurio/C=US"

# Create server extensions file for SAN (Subject Alternative Names)
cat > "${CERTS_DIR}/server-ext.cnf" << EOF
subjectAltName = DNS:apicurio-registry,DNS:apicurio-registry.default.svc,DNS:apicurio-registry.default.svc.cluster.local,DNS:localhost,IP:127.0.0.1
EOF

openssl x509 -req -days ${VALIDITY_DAYS} -in "${CERTS_DIR}/server.csr" \
    -CA "${CERTS_DIR}/ca-cert.pem" -CAkey "${CERTS_DIR}/ca-key.pem" \
    -CAcreateserial -out "${CERTS_DIR}/server-cert.pem" \
    -extfile "${CERTS_DIR}/server-ext.cnf"

# 3. Generate Client Certificate
echo "3. Generating Client Certificate..."
openssl genrsa -out "${CERTS_DIR}/client-key.pem" 4096
openssl req -new -key "${CERTS_DIR}/client-key.pem" \
    -out "${CERTS_DIR}/client.csr" \
    -subj "/CN=apicurio-client/O=Apicurio/C=US"

openssl x509 -req -days ${VALIDITY_DAYS} -in "${CERTS_DIR}/client.csr" \
    -CA "${CERTS_DIR}/ca-cert.pem" -CAkey "${CERTS_DIR}/ca-key.pem" \
    -CAcreateserial -out "${CERTS_DIR}/client-cert.pem"

# 4. Create Server Keystore (PKCS12 format) with full certificate chain
echo "4. Creating Server Keystore..."
openssl pkcs12 -export -in "${CERTS_DIR}/server-cert.pem" \
    -inkey "${CERTS_DIR}/server-key.pem" \
    -certfile "${CERTS_DIR}/ca-cert.pem" \
    -out "${CERTS_DIR}/server-keystore.p12" \
    -name server -passout pass:${PASSWORD}

# Convert to JKS format (for compatibility)
keytool -importkeystore -noprompt \
    -srckeystore "${CERTS_DIR}/server-keystore.p12" -srcstoretype PKCS12 -srcstorepass ${PASSWORD} \
    -destkeystore "${CERTS_DIR}/server-keystore.jks" -deststoretype JKS -deststorepass ${PASSWORD}

# 5. Create Server Truststore with Client CA certificate
echo "5. Creating Server Truststore..."
keytool -import -noprompt -trustcacerts -alias ca \
    -file "${CERTS_DIR}/ca-cert.pem" \
    -keystore "${CERTS_DIR}/server-truststore.jks" \
    -storepass ${PASSWORD}

# Also create PKCS12 format
keytool -importkeystore -noprompt \
    -srckeystore "${CERTS_DIR}/server-truststore.jks" -srcstoretype JKS -srcstorepass ${PASSWORD} \
    -destkeystore "${CERTS_DIR}/server-truststore.p12" -deststoretype PKCS12 -deststorepass ${PASSWORD}

# 6. Create Client Keystore with full certificate chain
echo "6. Creating Client Keystore..."
openssl pkcs12 -export -in "${CERTS_DIR}/client-cert.pem" \
    -inkey "${CERTS_DIR}/client-key.pem" \
    -certfile "${CERTS_DIR}/ca-cert.pem" \
    -out "${CERTS_DIR}/client-keystore.p12" \
    -name client -passout pass:${PASSWORD}

# Convert to JKS format
keytool -importkeystore -noprompt \
    -srckeystore "${CERTS_DIR}/client-keystore.p12" -srcstoretype PKCS12 -srcstorepass ${PASSWORD} \
    -destkeystore "${CERTS_DIR}/client-keystore.jks" -deststoretype JKS -deststorepass ${PASSWORD}

# 7. Create Client Truststore with Server CA certificate
echo "7. Creating Client Truststore..."
keytool -import -noprompt -trustcacerts -alias ca \
    -file "${CERTS_DIR}/ca-cert.pem" \
    -keystore "${CERTS_DIR}/client-truststore.jks" \
    -storepass ${PASSWORD}

# Also create PKCS12 format
keytool -importkeystore -noprompt \
    -srckeystore "${CERTS_DIR}/client-truststore.jks" -srcstoretype JKS -srcstorepass ${PASSWORD} \
    -destkeystore "${CERTS_DIR}/client-truststore.p12" -deststoretype PKCS12 -deststorepass ${PASSWORD}

# Clean up intermediate files
rm -f "${CERTS_DIR}"/*.csr "${CERTS_DIR}"/*.srl "${CERTS_DIR}"/server-ext.cnf "${CERTS_DIR}"/ca-ext.cnf

echo ""
echo "Certificate generation completed!"
echo ""
echo "Generated files:"
echo "  CA Certificate: ca-cert.pem"
echo "  Server Keystore: server-keystore.jks, server-keystore.p12"
echo "  Server Truststore: server-truststore.jks, server-truststore.p12"
echo "  Client Keystore: client-keystore.jks, client-keystore.p12"
echo "  Client Truststore: client-truststore.jks, client-truststore.p12"
echo ""
echo "Password for all keystores/truststores: ${PASSWORD}"
