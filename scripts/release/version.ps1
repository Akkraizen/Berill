param([string]$Tag, [string]$GithubOutput)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$values = Get-Content -LiteralPath (Join-Path $root 'version.properties') -Raw | ConvertFrom-StringData
$name = $values.versionName
$code = 0
if ($name -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') { throw 'versionName must be MAJOR.MINOR.PATCH.' }
$parts = $name.Split('.') | ForEach-Object { [long]$_ }
if ($parts[0] -gt 255 -or $parts[1] -gt 255 -or $parts[2] -gt 65535) { throw 'versionName exceeds Windows Installer limits.' }
if (-not [int]::TryParse($values.versionCode, [ref]$code) -or $code -lt 1 -or $code -gt 2100000000) { throw 'versionCode must be between 1 and 2100000000.' }
if ($Tag -and $Tag -cne "v$name") { throw "Tag must match version.properties: v$name" }
if ($GithubOutput) { Add-Content -LiteralPath $GithubOutput -Value "name=$name`ncode=$code" -Encoding utf8 }
[pscustomobject]@{ Name = $name; Code = $code }
