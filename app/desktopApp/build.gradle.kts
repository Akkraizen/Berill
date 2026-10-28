import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

dependencies {
  implementation(project(":app:shared"))
  implementation(project(":mcp"))

  implementation(compose.desktop.currentOs)
  implementation(libs.kotlinx.coroutinesSwing)
  implementation(libs.jna)
  testImplementation(libs.kotlin.testJunit)

  implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
  application {
    mainClass = "dev.akkraizen.berill.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
      packageName = "Berill"
      packageVersion = rootProject.extra["appVersionName"] as String
      modules("java.sql", "java.naming", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")
      vendor = "Akkraizen"
      copyright = "© 2026 Akkraizen"
      windows {
        upgradeUuid = "037c9dec-c551-3b2f-b65e-d0959a559736"
        iconFile.set(rootProject.file("scripts/windows-installer/assets/Berill.ico"))
        menuGroup = "Berill"
        shortcut = true
      }
      macOS {
        bundleID = "dev.akkraizen.berill"
        dockName = "Berill"
        appCategory = "public.app-category.productivity"
        minimumSystemVersion = "12.0"
        iconFile.set(rootProject.file("scripts/macos/Berill.icns"))
      }
    }

    buildTypes.release.proguard {
      configurationFiles.from(project.file("proguard-rules.pro"))
      obfuscate.set(false)
    }
  }
}
