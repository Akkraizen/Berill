package dev.akkraizen.berill.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.*

class AndroidUpdateInstaller(private val context: Context) : FileUpdateInstaller(File(context.cacheDir, "updates")) {
  override val platform = "android"
  @Suppress("DEPRECATION")
  override suspend fun validate(path: String, update: AppUpdate) {
    super.validate(path, update)
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
      val archive = pm.getPackageArchiveInfo(packageFile(path).absolutePath, flags) ?: throw UpdateException(UpdateFailure.PACKAGE)
      val installed = pm.getPackageInfo(context.packageName, flags)
      val nextCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
      val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
      if (archive.packageName != context.packageName || archive.versionName != update.version || nextCode <= currentCode) throw UpdateException(UpdateFailure.PACKAGE)
      fun signers(info: PackageInfo, history: Boolean): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.let {
          if (history && !it.hasMultipleSigners()) it.signingCertificateHistory else it.apkContentsSigners
        } else info.signatures
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
      }
      val current = signers(installed, false)
      val next = signers(archive, false)
      val matches = if (current.size > 1 || next.size > 1) current == next else signers(archive, true).containsAll(current)
      if (current.isEmpty() || next.isEmpty() || !matches) throw UpdateException(UpdateFailure.PACKAGE)
    }
  }
  override suspend fun install(path: String, update: AppUpdate): InstallResult = withContext(Dispatchers.Main) {
    if (!context.packageManager.canRequestPackageInstalls()) {
      context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return@withContext InstallResult.PERMISSION_REQUIRED
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", packageFile(path))
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    InstallResult.OPENED
  }
}
