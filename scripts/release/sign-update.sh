#!/usr/bin/env bash
# Signs the installation packages of one release for the in-app updater.
#   UPDATE_SIGNING_KEY="$(cat key.pem)" bash scripts/release/sign-update.sh <dir> <version>
# Writes <dir>/Berill-<version>-signatures.json: {"files": {"<file name>": "<base64 signature>"}}.
# The signed message is defined in protocol/.../UpdateSignature.kt.
set -euo pipefail

dir="${1:?directory with the release files is required}"
version="${2:?version is required}"
: "${UPDATE_SIGNING_KEY:?UPDATE_SIGNING_KEY must contain the PEM private key}"

key=$(mktemp)
trap 'rm -f "$key"' EXIT
printf '%s\n' "$UPDATE_SIGNING_KEY" > "$key"

entries=()
for path in "$dir"/Berill-"$version"-android.apk "$dir"/Berill-"$version"-windows.exe "$dir"/Berill-"$version"-Setup.exe \
  "$dir"/Berill-"$version"-macos.dmg "$dir"/Berill-"$version"-linux.deb "$dir"/Berill-"$version"-linux.rpm; do
  [ -f "$path" ] || continue
  name=$(basename "$path")
  sha256=$(sha256sum "$path" | cut -d' ' -f1)
  size=$(stat -c %s "$path")
  signature=$(printf 'berill-update-v1\n%s\n%s\n%s\n' "$name" "$sha256" "$size" | openssl dgst -sha256 -sign "$key" | base64 | tr -d '\n')
  entries+=("\"$name\":\"$signature\"")
  echo "Signed $name"
done
if [ ${#entries[@]} -eq 0 ]; then
  echo "No packages for $version in $dir" >&2
  exit 1
fi
(IFS=,; printf '{"files":{%s}}\n' "${entries[*]}") > "$dir/Berill-$version-signatures.json"
