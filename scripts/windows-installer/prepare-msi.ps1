param(
  [Parameter(Mandatory)][string]$Source,
  [Parameter(Mandatory)][string]$Destination
)
$ErrorActionPreference = 'Stop'
$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
if ($sourcePath -eq $destinationPath) { throw 'The upgrade MSI must be a separate staged copy.' }
New-Item -ItemType Directory -Force -Path (Split-Path $destinationPath -Parent) | Out-Null
Copy-Item -LiteralPath $sourcePath -Destination $destinationPath -Force
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $null
try {
  $database = $installer.OpenDatabase($destinationPath, 1)
  $sequence = @{}
  $view = $database.OpenView('SELECT Action, Sequence FROM InstallExecuteSequence')
  try {
    [void]$view.Execute()
    while ($row = $view.Fetch()) {
      $name = $row.GetType().InvokeMember('StringData', 'GetProperty', $null, $row, @(1))
      $sequence[$name] = [int]$row.GetType().InvokeMember('StringData', 'GetProperty', $null, $row, @(2))
    }
  } finally { [void]$view.Close(); [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) }
  foreach ($required in @('InstallInitialize', 'RemoveExistingProducts', 'ProcessComponents', 'InstallFinalize')) {
    if (-not $sequence.ContainsKey($required)) { throw "Missing MSI action: $required" }
  }
  $target = $sequence['InstallInitialize'] + 1
  while ($target -in $sequence.Values) { $target++ }
  if ($target -ge $sequence['ProcessComponents']) { throw 'No safe upgrade slot before ProcessComponents.' }
  $view = $database.OpenView("UPDATE InstallExecuteSequence SET Sequence = $target WHERE Action = 'RemoveExistingProducts'")
  try { [void]$view.Execute() }
  finally { [void]$view.Close(); [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) }
  $summary = $database.SummaryInformation(1)
  try {
    $packageCode = '{' + [guid]::NewGuid().ToString().ToUpperInvariant() + '}'
    [void]$summary.GetType().InvokeMember('Property', 'SetProperty', $null, $summary, @(9, $packageCode))
    [void]$summary.Persist()
  } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($summary) }
  [void]$database.Commit()
} finally {
  if ($database) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }
  [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer)
}
Get-Item -LiteralPath $destinationPath
