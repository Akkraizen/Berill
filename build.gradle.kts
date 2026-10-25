import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidMultiplatformLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.ktor) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.sqldelight) apply false
}

val releaseVersion = Properties().apply {
  rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = releaseVersion.getProperty("versionName")
val appVersionCode = releaseVersion.getProperty("versionCode")?.toIntOrNull()
require(appVersionName != null && Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(appVersionName)) {
  "versionName must be MAJOR.MINOR.PATCH in version.properties"
}
val versionParts = appVersionName.split('.').map { it.toLong() }
require(versionParts[0] <= 255 && versionParts[1] <= 255 && versionParts[2] <= 65535) {
  "versionName exceeds Windows Installer limits"
}
require(appVersionCode != null && appVersionCode in 1..2100000000) {
  "versionCode must be between 1 and 2100000000 in version.properties"
}
extra["appVersionName"] = appVersionName
extra["appVersionCode"] = appVersionCode

allprojects {
  version = appVersionName
  dependencyLocking {
    lockAllConfigurations()
  }
}

tasks.register("resolveAndLockAll") {
  group = "verification"
  description =
    "Resolves all configurations of all modules — for --write-locks and --write-verification-metadata"
  notCompatibleWithConfigurationCache("iterates over configurations of all modules")
  doLast {
    allprojects.forEach { p ->
      p.configurations
        .filter { it.isCanBeResolved }
        .forEach { c ->
          runCatching { c.resolve() }
        }
    }
  }
}

@CacheableTask
abstract class GenerateLocaleCatalog : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val localeFiles: ConfigurableFileCollection

  @get:Input
  abstract val catalogName: Property<String>

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    fun literal(text: String): String = buildString {
      append('"')
      text.forEach { char ->
        append(when (char) {
          '\\' -> "\\\\"
          '"' -> "\\\""
          '$' -> "\\\$"
          '\n' -> "\\n"
          '\r' -> "\\r"
          '\t' -> "\\t"
          else -> char.toString()
        })
      }
      append('"')
    }
    val name = catalogName.get()
    val target = outputDirectory.file("berill/generated/localization/$name.kt").get().asFile
    target.parentFile.mkdirs()
    val files = localeFiles.files.sortedBy { it.name }.map { file ->
      file.readText(Charsets.UTF_8).chunked(8000)
        .joinToString(", ", "listOf(", ").joinToString(\"\")", transform = ::literal)
    }
    target.writeText(
        "package berill.generated.localization\n\n" +
        "internal object $name {\n  val files: List<String> = listOf(\n" +
        files.joinToString(",\n") { "    $it" } + "\n  )\n}\n",
      Charsets.UTF_8
    )
  }
}

allprojects {
  pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val locales = layout.projectDirectory.dir("src/commonMain/localization")
    if (locales.asFile.isDirectory) {
      val objectName = project.name.split('-', '_').joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
      } + "CatalogData"
      val generate = tasks.register<GenerateLocaleCatalog>("generateLocalization") {
        group = "build"
        description = "Packages locale JSON files for common Kotlin code"
        localeFiles.from(fileTree(locales) { include("*.json") })
        catalogName.set(objectName)
        outputDirectory.set(layout.buildDirectory.dir("generated/localization"))
      }
      extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.named("commonMain") {
          kotlin.srcDir(generate.flatMap { it.outputDirectory })
        }
      }
    }
  }
}
