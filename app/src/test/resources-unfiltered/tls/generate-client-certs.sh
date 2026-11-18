#!/bin/bash
set -e

# Renews the mTLS test client certificate (CN=registry-client), signed by the
# registry-test key in registry-keystore.jks, so the truststores need no change.
# Regenerates client-cert.pem, client-keystore.p12 and client-keystore.jks,
# reusing client-key.pem.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PASSWORD="registrytest"
VALIDITY=3650  # 10 years
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Extracting the registry-test signing key..."
keytool -importkeystore -noprompt \
    -srckeystore registry-keystore.jks -srcstorepass $PASSWORD -srcalias registry-test \
    -destkeystore "$WORK/registry.p12" -deststoretype PKCS12 -deststorepass $PASSWORD
openssl pkcs12 -in "$WORK/registry.p12" -passin pass:$PASSWORD -nodes -nocerts -out "$WORK/ca-key.pem"
openssl pkcs12 -in "$WORK/registry.p12" -passin pass:$PASSWORD -nokeys -clcerts -out "$WORK/ca-cert.pem"

echo "Issuing the client certificate..."
openssl req -new -key client-key.pem -subj "/CN=registry-client/O=Apicurio/C=US" -out "$WORK/client.csr"
openssl x509 -req -in "$WORK/client.csr" -CA "$WORK/ca-cert.pem" -CAkey "$WORK/ca-key.pem" \
    -CAcreateserial -days $VALIDITY -sha256 -out client-cert.pem

echo "Building the client keystores..."
rm -f client-keystore.p12 client-keystore.jks
openssl pkcs12 -export -name client -inkey client-key.pem -in client-cert.pem \
    -certfile "$WORK/ca-cert.pem" -passout pass:$PASSWORD -out client-keystore.p12
keytool -importkeystore -noprompt \
    -srckeystore client-keystore.p12 -srcstoretype PKCS12 -srcstorepass $PASSWORD \
    -destkeystore client-keystore.jks -deststoretype JKS -deststorepass $PASSWORD -destkeypass $PASSWORD

echo "Done. Client certificate valid until: $(openssl x509 -in client-cert.pem -noout -enddate)"
