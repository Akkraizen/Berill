param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference = 'Stop'
$files = @(Get-ChildItem -LiteralPath $Directory -File | Where-Object { $_.Extension -in '.exe', '.apk', '.aab' } | Sort-Object Name)
if ($files.Count -eq 0) { throw 'No release files found.' }
$lines = $files | ForEach-Object { "$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)" }
[IO.File]::WriteAllText((Join-Path $Directory 'SHA256SUMS.txt'), (($lines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
