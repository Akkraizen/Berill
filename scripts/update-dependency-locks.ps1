$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
.\gradlew.bat --write-locks --write-verification-metadata sha256 `
    resolveAndLockAll `
    :app:desktopApp:jar `
    :app:androidApp:assembleDebug `
    :server:build `
    :external-plugins:counters:packageBerillPlugin
