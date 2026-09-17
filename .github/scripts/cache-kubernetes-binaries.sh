#!/usr/bin/env bash
set -euo pipefail

# Minikube's driver=none reads this cache before downloading control-plane binaries.
# Download with bounded retries and verify checksums before publishing cache entries.
version="${1:?Kubernetes version is required}"
cache="$HOME/.minikube/cache/linux/amd64/$version"
mkdir -p "$cache"
temporary=$(mktemp -d "$cache/.download.XXXXXX")
trap 'rm -rf "$temporary"' EXIT
for binary in kubelet kubeadm kubectl; do
    url="https://dl.k8s.io/release/$version/bin/linux/amd64/$binary"
    curl --fail --location --http1.1 --retry 5 --retry-all-errors \
        --connect-timeout 15 --max-time 60 --retry-max-time 120 \
        "$url.sha256" --output "$temporary/$binary.sha256"
    checksum=$(tr -d '\r\n' < "$temporary/$binary.sha256")
    if [[ ! "$checksum" =~ ^[[:xdigit:]]{64}$ ]]; then
        echo "Invalid SHA-256 checksum for $binary" >&2
        exit 1
    fi
    if [ -f "$cache/$binary" ] && printf '%s  %s\n' "$checksum" "$cache/$binary" | sha256sum --check --status; then
        continue
    fi
    curl --fail --location --http1.1 --retry 5 --retry-all-errors \
        --connect-timeout 15 --max-time 60 --retry-max-time 120 \
        "$url" --output "$temporary/$binary"
    printf '%s  %s\n' "$checksum" "$temporary/$binary" | sha256sum --check
    chmod +x "$temporary/$binary"
    mv "$temporary/$binary" "$cache/$binary"
done
