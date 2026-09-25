#!/bin/bash
# Generate self-signed certs for the test proxy
mkdir -p certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout certs/server.key -out certs/server.crt \
  -subj "/CN=localhost" 2>/dev/null
echo "Certificates generated in certs/"
