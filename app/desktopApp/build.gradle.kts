import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.util.UUID
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
  testImplementation(project(":plugins:recipes"))
  testImplementation(project(":plugins:workouts"))
  testImplementation(project(":plugins:health"))
  testImplementation(project(":plugins:books"))
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.sqldelight.sqliteDriver)

  implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
  application {
    mainClass = "dev.akkraizen.berill.MainKt"

    nativeDistributions {
      targetFormats(
        TargetFormat.Msi,
        TargetFormat.Exe,
        TargetFormat.Dmg,
        TargetFormat.Deb,
        TargetFormat.Rpm,
        TargetFormat.AppImage
      )
      packageName = "Berill"
      packageVersion = rootProject.extra["appVersionName"] as String
      modules("java.sql", "java.naming", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")
      vendor = "Akkraizen"
      copyright = "© 2026 Akkraizen"
      windows {
        upgradeUuid = "037c9dec-c551-3b2f-b65e-d0959a559736"
        menuGroup = "Berill"
        shortcut = true
        iconFile.set(rootProject.file("scripts/windows-installer/assets/Berill.ico"))
      }
      macOS {
        bundleID = "dev.akkraizen.berill"
        dockName = "Berill"
        appCategory = "public.app-category.productivity"
        minimumSystemVersion = "12.0"
        iconFile.set(rootProject.file("scripts/macos/Berill.icns"))
      }
      linux {
        packageName = "berill"
        appCategory = "Office;Productivity;"
        shortcut = true
        menuGroup = "Berill"
        iconFile.set(rootProject.file("scripts/linux/Berill.png"))
      }
    }

    buildTypes.release.proguard {
      configurationFiles.from(project.file("proguard-rules.pro"))
      obfuscate.set(false)
    }
  }
}


tasks.withType<Test>().configureEach {
  systemProperty(
    "berill.uiEvidenceDir",
    layout.buildDirectory.dir("test-evidence").get().asFile.absolutePath
  )
}

// Hot Reload 1.2 deletes its startup JAR directory when refreshing dependencies.
// Windows keeps these JARs locked while the previous JVM is alive (including tray instances).
// Track a launch ID so configuration-cache reuse cannot point a new launch at locked JARs.
abstract class HotReloadLaunchId : ValueSource<String, ValueSourceParameters.None> {
  override fun obtain(): String = UUID.randomUUID().toString()
}

if (System.getProperty("os.name").startsWith("Windows")) {
  plugins.withId("org.jetbrains.compose.hot-reload") {
    afterEvaluate {
      afterEvaluate {
        // Hot Reload 1.2 defers its setup by two evaluation stages. Resolve its lazy
        // task wiring first, under this project's Gradle lock, before overriding the directory.
        (tasks.findByName("hotRun") as? JavaExec)?.classpath?.files
        tasks.matching { it.name.startsWith("sync") && it.name.endsWith("StartupLibs") }.forEach { task ->
          // The sync task's type is private; preserve its original copy action and classpath wiring.
          val getter = task.javaClass.getMethod("getDestinationDir").apply { isAccessible = true }
          val destination = getter.invoke(task) as DirectoryProperty
          val launchId = providers.of(HotReloadLaunchId::class.java) {}.get()
          destination.set(layout.buildDirectory.dir("run/startup-libs/${task.name}/$launchId"))
        }
        // @Classpath ignores location-only changes; the argfile must still use the new paths.
        tasks.matching { it.name.startsWith("hot") && it.name.endsWith("Argfile") }.configureEach {
          outputs.upToDateWhen { false }
        }
      }
    }
  }
}
