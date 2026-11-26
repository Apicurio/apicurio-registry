#!/usr/bin/env bash
# Generates every certificate this example uses, in certs/ (nothing private is committed).
# Usage: ./certs/generate-certs.sh   (requires openssl and keytool; demo.sh runs it when needed)
#
#   ca-cert.pem                    the example CA: signs the server and the client certificates
#   server-cert.pem, -key.pem      the registries' certificate (localhost and the Compose names)
#   server-keystore.p12            the same certificate and key, as a PKCS12 key store
#   client-cert.pem, -key.pem      a client certificate signed by the example CA
#   client-keystore.p12, .jks      the same, as PKCS12 and JKS key stores
#   truststore.p12, .jks           the example CA, as PKCS12 and JKS trust stores
#   rogue-ca-cert.pem              another CA, which nothing trusts
#   rogue-client-cert.pem, -key.pem, rogue-client-keystore.p12
#                                  a client certificate signed by the rogue CA
#
# Every store's password is $STORE_PASSWORD (default: apicurio).
set -euo pipefail
cd "$(dirname "$0")"

PASSWORD=${STORE_PASSWORD:-apicurio}
DAYS=365

rm -f ./*.pem ./*.p12 ./*.jks ./*.csr ./*.srl ./*.cnf

# ca <name> <common name>: a self-signed CA
ca() {
  openssl req -x509 -newkey rsa:2048 -nodes -days "$DAYS" -keyout "$1-key.pem" -out "$1-cert.pem" \
    -subj "/CN=$2/O=Apicurio" \
    -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign" 2> /dev/null
}
# leaf <name> <common name> <ca> <extensions>: a certificate signed by <ca>
leaf() {
  openssl req -new -newkey rsa:2048 -nodes -keyout "$1-key.pem" -out "$1.csr" -subj "/CN=$2/O=Apicurio" 2> /dev/null
  printf '%s\n' "$4" > "$1.cnf"
  openssl x509 -req -days "$DAYS" -in "$1.csr" -CA "$3-cert.pem" -CAkey "$3-key.pem" -CAcreateserial \
    -out "$1-cert.pem" -extfile "$1.cnf" 2> /dev/null
}
# p12 <name> <ca>: <name>'s certificate and key, with the CA chain, as a PKCS12 key store
p12() {
  openssl pkcs12 -export -in "$1-cert.pem" -inkey "$1-key.pem" -certfile "$2-cert.pem" -name "$1" \
    -out "$1-keystore.p12" -passout "pass:$PASSWORD"
}

ca ca "Apicurio Registry Example CA"
ca rogue-ca "Rogue CA"

# The names clients use: localhost from the host, the service names inside Compose. Not 127.0.0.1:
# the demo calls that address to show hostname verification
leaf server registry ca "subjectAltName=DNS:localhost,DNS:registry,DNS:registry-mtls
extendedKeyUsage=serverAuth"
leaf client registry-client ca "extendedKeyUsage=clientAuth"
leaf rogue-client registry-client rogue-ca "extendedKeyUsage=clientAuth"

p12 server ca
p12 client ca
p12 rogue-client rogue-ca
keytool -importkeystore -noprompt -srckeystore client-keystore.p12 -srcstoretype PKCS12 -srcstorepass "$PASSWORD" \
  -destkeystore client-keystore.jks -deststoretype JKS -deststorepass "$PASSWORD" 2> /dev/null

keytool -importcert -noprompt -alias ca -file ca-cert.pem -keystore truststore.p12 -storetype PKCS12 -storepass "$PASSWORD"
keytool -importcert -noprompt -alias ca -file ca-cert.pem -keystore truststore.jks -storetype JKS -storepass "$PASSWORD"

# The CA keys are only needed to sign: delete them
rm -f ./*.csr ./*.srl ./*.cnf ca-key.pem rogue-ca-key.pem
# The registry containers run as another user: let them read the files (for this demo only)
chmod a+r ./*.pem ./*.p12 ./*.jks
echo "Certificates generated in $(pwd) (store password: $PASSWORD)"
