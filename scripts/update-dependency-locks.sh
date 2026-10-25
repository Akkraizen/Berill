#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/.."
./gradlew --write-locks --write-verification-metadata sha256 \
    resolveAndLockAll \
    :app:desktopApp:jar \
    :app:androidApp:assembleDebug \
    :server:build \
    :external-plugins:counters:packageBerillPlugin
