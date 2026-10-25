param(
  [string]$Compiler,
  [string]$OutputDirectory,
  [string]$MsiPath,
  [switch]$SkipMsiBuild,
  [switch]$Preview,
  [switch]$CheckUpgrade
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $OutputDirectory) {
  $OutputDirectory = Join-Path $projectRoot 'app\desktopApp\build\compose\binaries\main-release\branded-exe'
}
if (-not $Compiler) {
  $command = Get-Command ISCC.exe -ErrorAction SilentlyContinue
  if ($command) { $Compiler = $command.Source }
  foreach ($candidate in @(
    "${env:ProgramFiles}\Inno Setup 7\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "$env:LOCALAPPDATA\Programs\Inno Setup 7\ISCC.exe"
  )) {
    if (-not $Compiler -and (Test-Path -LiteralPath $candidate)) { $Compiler = $candidate }
  }
}
if (-not $Compiler -or -not (Test-Path -LiteralPath $Compiler)) {
  throw 'Install Inno Setup 7.1 or newer, or pass -Compiler with the path to ISCC.exe.'
}
Push-Location $projectRoot
try {
  $release = & (Join-Path $PSScriptRoot "..\release\version.ps1")
  & (Join-Path $PSScriptRoot 'generate-assets.ps1')
  if (-not $SkipMsiBuild -and -not $MsiPath) {
    & .\gradlew.bat :app:desktopApp:packageReleaseMsi --no-configuration-cache --console=plain
    if ($LASTEXITCODE -ne 0) { throw "MSI build failed: $LASTEXITCODE" }
  }
  $msi = if ($MsiPath) {
    Get-Item -LiteralPath $MsiPath
  } else {
    Get-ChildItem -LiteralPath 'app\desktopApp\build\compose\binaries\main-release\msi' -Filter '*.msi' |
      Sort-Object LastWriteTime -Descending | Select-Object -First 1
  }
  if (-not $msi) { throw 'Release MSI not found. Run without -SkipMsiBuild.' }
  if ($msi.PSIsContainer -or $msi.Extension -ne '.msi') { throw 'MsiPath must point to an MSI file.' }
  $installer = New-Object -ComObject WindowsInstaller.Installer
  $database = $installer.OpenDatabase($msi.FullName, 0)
  function Read-MsiProperty([string]$Name) {
    $view = $database.OpenView("SELECT Value FROM Property WHERE Property = '$Name'")
    [void]$view.Execute()
    $row = $view.Fetch()
    if (-not $row) { throw "Missing MSI property: $Name" }
    $row.GetType().InvokeMember('StringData', 'GetProperty', $null, $row, @(1))
    [void]$view.Close()
  }
  $version = Read-MsiProperty 'ProductVersion'
  if ($version -ne $release.Name) { throw "MSI version $version does not match version.properties ($($release.Name))." }
  $productCode = Read-MsiProperty 'ProductCode'
  $upgradeCode = Read-MsiProperty 'UpgradeCode'
  [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database)
  [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer)
  $stagingId = [guid]::NewGuid().ToString('N')
  $stagedPath = Join-Path $projectRoot "app\desktopApp\build\windows-installer-msi\$stagingId\Berill-$version-upgrade.msi"
  $msi = & (Join-Path $PSScriptRoot 'prepare-msi.ps1') -Source $msi.FullName -Destination $stagedPath
  New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
  $compilerArgs = @("/DAppVersion=$version", "/DProductCode=$productCode", "/DUpgradeCode=$upgradeCode", "/DMsiPath=$($msi.FullName)", "/DOutputPath=$OutputDirectory")
  if ($Preview) { $compilerArgs += '/DPreview=1' }
  if ($CheckUpgrade) { $compilerArgs += '/DUpgradeCheck=1'; $compilerArgs += '/DPreview=1' }
  & $Compiler @compilerArgs (Join-Path $PSScriptRoot 'Berill.iss')
  if ($LASTEXITCODE -ne 0) { throw "EXE build failed: $LASTEXITCODE" }
  Get-Item (Join-Path $OutputDirectory "Berill-$version-Setup.exe")
} finally {
  Pop-Location
}
