import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.sqldelight)
}

kotlin {
  applyDefaultHierarchyTemplate {
    common {
      group("jvmCommon") {
        withCompilations {
          it.target.platformType == KotlinPlatformType.jvm || it.target.platformType == KotlinPlatformType.androidJvm
        }
      }
    }
  }

  jvm()

  android {
    namespace = "dev.akkraizen.berill.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core-api"))
      api(project(":protocol"))
      implementation(project(":plugin-sdk"))
      implementation(libs.ktor.clientCore)
      implementation(libs.ktor.clientContentNegotiation)
      implementation(libs.ktor.serializationJson)
      implementation(libs.sqldelight.coroutines)
      implementation(libs.coil.networkKtor3)
      implementation(libs.materialKolor)
    }
    androidMain.dependencies {
      implementation(libs.kotlinx.coroutinesAndroid)
      implementation(libs.androidx.activity.compose)
      implementation(libs.ktor.clientOkhttp)
      implementation(libs.sqldelight.androidDriver)
    }
    jvmMain.dependencies {
      implementation(libs.kotlinx.coroutinesSwing)
      implementation(libs.ktor.clientOkhttp)
      implementation(libs.sqldelight.sqliteDriver)
      implementation(libs.jna)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
    jvmTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

abstract class GenerateBuildInfo : DefaultTask() {
  @get:Input abstract val versionName: Property<String>
  @get:Input abstract val versionCode: Property<Int>
  @get:Input abstract val updatesUrl: Property<String>
  @get:Internal abstract val repository: DirectoryProperty
  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  private fun git(vararg args: String): String = runCatching {
    val process = ProcessBuilder(listOf("git") + args)
      .directory(repository.get().asFile)
      .redirectErrorStream(true)
      .start()
    val out = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0) out else ""
  }.getOrDefault("")

  @TaskAction
  fun generate() {
    val commit = git("rev-parse", "--short=8", "HEAD").ifEmpty { "unknown" }
    val commitTime = git("log", "-1", "--format=%cI")
    val dirty = git("status", "--porcelain", "--untracked-files=no").isNotEmpty()
    fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
    val text = """
      |package dev.akkraizen.berill.core
      |
      |object BuildInfo {
      |  const val VERSION_NAME: String = ${q(versionName.get())}
      |  const val VERSION_CODE: Int = ${versionCode.get()}
      |  const val COMMIT: String = ${q(commit)}
      |  const val COMMIT_TIME: String = ${q(commitTime)}
      |  const val DIRTY: Boolean = $dirty
      |  const val UPDATES_URL: String = ${q(updatesUrl.get())}
      |}
      |""".trimMargin()
    val target = File(outputDirectory.get().asFile, "dev/akkraizen/berill/core/BuildInfo.kt")
    target.parentFile.mkdirs()
    if (!target.isFile || target.readText() != text) target.writeText(text)
  }
}

val generateBuildInfo = tasks.register<GenerateBuildInfo>("generateBuildInfo") {
  group = "build"
  description = "Generates BuildInfo (version and git commit)"
  versionName.set(rootProject.extra["appVersionName"] as String)
  versionCode.set(rootProject.extra["appVersionCode"] as Int)
  updatesUrl.set(providers.gradleProperty("berill.updatesUrl").orElse(""))
  repository.set(rootProject.layout.projectDirectory)
  outputDirectory.set(layout.buildDirectory.dir("generated/buildinfo"))
  outputs.upToDateWhen { false }
}

kotlin.sourceSets.named("commonMain") {
  kotlin.srcDir(generateBuildInfo.flatMap { it.outputDirectory })
}

sqldelight {
  databases {
    create("BerillDatabase") {
      packageName.set("dev.akkraizen.berill.core.db")
    }
  }
}
