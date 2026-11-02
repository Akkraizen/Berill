param(
  [string]$Out = 'berill-update-signing.pem'
)
$ErrorActionPreference = 'Stop'

$path = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Out)
if (Test-Path -LiteralPath $path) { throw "$path already exists; refusing to overwrite a signing key" }

function Get-Fixed([byte[]]$value) {
  if ($value.Length -gt 32) { throw 'Unexpected P-256 key length.' }
  $fixed = New-Object byte[] 32
  [Array]::Copy($value, 0, $fixed, 32 - $value.Length, $value.Length)
  return ,$fixed
}

function Join-Bytes([byte[][]]$parts) {
  $stream = New-Object IO.MemoryStream
  foreach ($part in $parts) { $stream.Write($part, 0, $part.Length) }
  return ,$stream.ToArray()
}

function ConvertFrom-Hex([string]$hex) {
  $bytes = New-Object byte[] ($hex.Length / 2)
  for ($i = 0; $i -lt $bytes.Length; $i++) { $bytes[$i] = [Convert]::ToByte($hex.Substring($i * 2, 2), 16) }
  return ,$bytes
}

$ecdsa = [Security.Cryptography.ECDsa]::Create([Security.Cryptography.ECCurve+NamedCurves]::nistP256)
try {
  $parameters = $ecdsa.ExportParameters($true)
} finally {
  $ecdsa.Dispose()
}
$d = Get-Fixed $parameters.D
$point = Join-Bytes @([byte[]]@(4), (Get-Fixed $parameters.Q.X), (Get-Fixed $parameters.Q.Y))

$privateDer = Join-Bytes @(
  (ConvertFrom-Hex '30770201010420'), $d,
  (ConvertFrom-Hex 'a00a06082a8648ce3d030107a144034200'), $point
)
$publicDer = Join-Bytes @((ConvertFrom-Hex '3059301306072a8648ce3d020106082a8648ce3d030107034200'), $point)

$body = [Convert]::ToBase64String($privateDer, [Base64FormattingOptions]::InsertLineBreaks) -replace "`r`n", "`n"
$pem = "-----BEGIN EC PRIVATE KEY-----`n$body`n-----END EC PRIVATE KEY-----`n"

$directory = Split-Path -Parent $path
if ($directory -and -not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
$stream = New-Object IO.FileStream($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
  $bytes = [Text.Encoding]::ASCII.GetBytes($pem)
  $stream.Write($bytes, 0, $bytes.Length)
} finally {
  $stream.Dispose()
}
[Array]::Clear($d, 0, $d.Length)
[Array]::Clear($privateDer, 0, $privateDer.Length)

if ($env:OS -eq 'Windows_NT') {
  $user = [Security.Principal.WindowsIdentity]::GetCurrent().User
  $acl = New-Object Security.AccessControl.FileSecurity
  $acl.SetAccessRuleProtection($true, $false)
  $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($user, 'FullControl', 'Allow')))
  Set-Acl -LiteralPath $path -AclObject $acl
} else {
  chmod 600 $path
}

Write-Host "Private key: $path"
Write-Output "berill.updatePublicKey=$([Convert]::ToBase64String($publicDer))"
