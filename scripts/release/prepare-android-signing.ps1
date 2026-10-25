$ErrorActionPreference = 'Stop'
foreach ($name in @('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Missing GitHub Actions secret: $name" }
}
if (-not $env:RUNNER_TEMP -or -not $env:GITHUB_ENV) { throw 'Run this script inside GitHub Actions.' }
$path = Join-Path $env:RUNNER_TEMP 'berill-release.jks'
$bytes = [Convert]::FromBase64String($env:ANDROID_KEYSTORE_BASE64)
if ($bytes.Length -eq 0) { throw 'Android keystore is empty.' }
[IO.File]::WriteAllBytes($path, $bytes)
Add-Content -LiteralPath $env:GITHUB_ENV -Value "ANDROID_KEYSTORE_PATH=$path" -Encoding utf8
