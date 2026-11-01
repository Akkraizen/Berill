package dev.akkraizen.berill.core.update

import dev.akkraizen.berill.core.sync.clientPlatform
import java.awt.Desktop
import java.io.File
import java.util.Base64
import kotlinx.coroutines.*

class DesktopUpdateInstaller(directory: File, private val launcher: String? = System.getProperty("jpackage.app-path")) : FileUpdateInstaller(directory) {
  override val platform = clientPlatform
  override val previousFailure: UpdateFailure? get() {
    val marker = File(directory, "installation.failed")
    return if (marker.isFile) { marker.delete(); UpdateFailure.INSTALL } else null
  }
  override val available get() = platform != "windows" || launcher?.let { File(it).isFile } == true
  private fun quote(value: String) = "'" + value.replace("'", "''") + "'"
  private fun powershell(script: String): Process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand",
    Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()

  override suspend fun validate(path: String, update: AppUpdate) {
    super.validate(path, update)
    if (platform != "windows") return
    withContext(Dispatchers.IO) {
      if (!available || !path.endsWith(".exe")) throw UpdateException(UpdateFailure.UNSUPPORTED)
      val script = """
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}launcherDirectory = [IO.Path]::GetDirectoryName(${quote(requireNotNull(launcher))}).TrimEnd([IO.Path]::DirectorySeparatorChar)
        ${'$'}entries = Get-ItemProperty -Path @('HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*', 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*', 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*') -ErrorAction SilentlyContinue
        ${'$'}installed = ${'$'}entries | Where-Object {
          ${'$'}_.DisplayName -eq 'Berill' -and (
            ([string]${'$'}_.InstallLocation).TrimEnd([IO.Path]::DirectorySeparatorChar) -eq ${'$'}launcherDirectory -or
            ([string]${'$'}_.DisplayIcon).IndexOf(${'$'}launcherDirectory + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -ge 0)
        }
        if (-not ${'$'}installed) { exit 2 }
        ${'$'}current = Get-AuthenticodeSignature -LiteralPath ${quote(requireNotNull(launcher))}
        ${'$'}next = Get-AuthenticodeSignature -LiteralPath ${quote(path)}
        if (${'$'}next.Status -notin @('Valid', 'NotSigned')) { exit 1 }
        if (${'$'}current.Status -eq 'Valid' -and (${'$'}next.Status -ne 'Valid' -or ${'$'}next.SignerCertificate.Thumbprint -ne ${'$'}current.SignerCertificate.Thumbprint)) { exit 1 }
      """.trimIndent()
      val process = powershell(script)
      if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw UpdateException(UpdateFailure.INSTALL) }
      if (process.exitValue() == 2) throw UpdateException(UpdateFailure.UNSUPPORTED)
      if (process.exitValue() != 0) throw UpdateException(UpdateFailure.PACKAGE)
    }
  }
  override suspend fun install(path: String, update: AppUpdate): InstallResult = withContext(Dispatchers.IO) {
    val file = packageFile(path)
    if (platform != "windows") { Desktop.getDesktop().open(file); return@withContext InstallResult.OPENED }
    val executable = requireNotNull(launcher)
    val hash = requireNotNull(update.sha256)
    val script = windowsInstallScript(file.absolutePath, executable, ProcessHandle.current().pid(), hash)
    val process = powershell(script)
    process.outputStream.close()
    InstallResult.EXIT_REQUIRED
  }
  internal fun windowsInstallScript(path: String, launcher: String, processId: Long, hash: String): String = """
    ${'$'}ErrorActionPreference = 'Stop'
    ${'$'}package = ${quote(path)}
    ${'$'}launcher = ${quote(launcher)}
    ${'$'}log = ${quote(File(directory, "installation.log").absolutePath)}
    try {
      if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { Wait-Process -Id $processId -Timeout 120 }
      if ((Get-FileHash -LiteralPath ${'$'}package -Algorithm SHA256).Hash.ToLowerInvariant() -ne ${quote(hash)}) { throw 'Package checksum changed' }
      ${'$'}result = Start-Process -FilePath ${'$'}package -ArgumentList @('/SILENT', '/SUPPRESSMSGBOXES', '/NORESTART') -Verb RunAs -Wait -PassThru
      if (${'$'}result.ExitCode -notin @(0, 3010)) { throw "Installer exit code: ${'$'}(${'$'}result.ExitCode)" }
      Remove-Item -LiteralPath ${'$'}package
      Remove-Item -LiteralPath ${quote(File(directory, "installation.failed").absolutePath)} -ErrorAction SilentlyContinue
    } catch { ${'$'}_ | Out-String | Set-Content -LiteralPath ${'$'}log; Set-Content -LiteralPath ${quote(File(directory, "installation.failed").absolutePath)} -Value 'failed' }
    if (Test-Path -LiteralPath ${'$'}launcher) { Start-Process -FilePath ${'$'}launcher }
  """.trimIndent()
}
