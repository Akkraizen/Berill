#!/usr/bin/env bash
# Creates the key pair that signs in-app update packages (ECDSA P-256).
#   bash scripts/release/generate-update-key.sh [output.pem]
# Store the private key as the GitHub secret UPDATE_SIGNING_KEY (environment "updates"), keep an
# offline copy, and never commit it. Put the printed public key into gradle.properties as
# berill.updatePublicKey. See docs/UPDATES.md.
set -euo pipefail

out="${1:-berill-update-signing.pem}"
if [ -e "$out" ]; then
  echo "$out already exists; refusing to overwrite a signing key" >&2
  exit 1
fi
umask 077
openssl ecparam -name prime256v1 -genkey -noout -out "$out"
public=$(openssl ec -in "$out" -pubout -outform DER 2>/dev/null | base64 | tr -d '\n')
echo "Private key: $out"
echo "berill.updatePublicKey=$public"
