#!/usr/bin/env bash
set -euo pipefail

dir="${1:?releases directory is required}"
keep="${KEEP:-3}"
cd "$dir"

shopt -s nullglob
incoming=(.incoming/Berill-*)
if [ ${#incoming[@]} -eq 0 ]; then
  echo "Nothing to publish in $dir/.incoming" >&2
  exit 1
fi
for file in "${incoming[@]}"; do
  mv -f "$file" .
done
rmdir .incoming 2>/dev/null || true

files=(Berill-*-android.apk Berill-*-Setup.exe Berill-*-macos.dmg)
mapfile -t versions < <(printf '%s\n' "${files[@]}" | sed -E 's/^Berill-([0-9]+(\.[0-9]+)+)-.*/\1/' | sort -u -V)
count=${#versions[@]}
if [ "$count" -gt "$keep" ]; then
  for version in "${versions[@]:0:count-keep}"; do
    rm -f "Berill-$version-android.apk" "Berill-$version-Setup.exe" "Berill-$version-macos.dmg"
    echo "Removed $version"
  done
fi
ls -l Berill-* 2>/dev/null || true
