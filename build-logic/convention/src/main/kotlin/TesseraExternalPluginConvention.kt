import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.configure
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.util.Properties

class TesseraExternalPluginConvention : Plugin<Project> {
  override fun apply(project: Project): Unit =
    with(project) {
      with(pluginManager) {
        apply("org.jetbrains.kotlin.multiplatform")
        apply("org.jetbrains.kotlin.plugin.serialization")
      }
      extensions.configure<KotlinMultiplatformExtension> {
        jvm()
        sourceSets.getByName("commonMain").dependencies { implementation(project(":plugin-sdk")) }
      }

      val jvmJar = tasks.named("jvmJar", Jar::class.java)
      val outDir = layout.buildDirectory.dir("tessera")
      val dexDir = layout.buildDirectory.dir("tessera/dex")
      val sdk = androidSdkDir(rootDir)
      val d8 =
        sdk?.let {
          newestVersionDir(File(it, "build-tools"))?.let { v -> File(v, "lib/d8.jar") }
        }
          ?.takeIf { it.isFile }
      val androidJar =
        sdk?.let {
          newestVersionDir(File(it, "platforms"))?.let { p -> File(p, "android.jar") }
        }
          ?.takeIf { it.isFile }

      val dex =
        if (d8 != null && androidJar != null) {
          val dependencies =
            objects.fileCollection().from(configurations.named("jvmRuntimeClasspath"))
          val pluginJar = objects.fileCollection().from(jvmJar)
          tasks.register("dexTesseraPlugin", JavaExec::class.java) {
            group = "tessera"
            description = "Converts plugin classes into classes.dex for Android (d8)"
            dependsOn(jvmJar)
            inputs.files(pluginJar, dependencies)
            outputs.dir(dexDir)
            classpath(d8)
            mainClass.set("com.android.tools.r8.D8")
            argumentProviders.add(
              CommandLineArgumentProvider {
                dexDir.get().asFile.apply {
                  deleteRecursively()
                  mkdirs()
                }
                buildList {
                  addAll(
                    listOf(
                      "--release",
                      "--min-api",
                      "26",
                      "--output",
                      dexDir.get().asFile.absolutePath
                    )
                  )
                  addAll(listOf("--lib", androidJar.absolutePath))
                  dependencies.files
                    .filter { it.exists() }
                    .forEach { addAll(listOf("--classpath", it.absolutePath)) }
                  add(pluginJar.singleFile.absolutePath)
                }
              },
            )
          }
        } else {
          logger.warn(
            "Tessera: Android SDK (d8) not found — package `$name` will be Windows-only. Specify sdk.dir in local.properties."
          )
          null
        }

      tasks.register("packageTesseraPlugin", Zip::class.java) {
        group = "tessera"
        description = "Packages third-party plugin .tplugin"
        archiveFileName.set("${project.name}.tplugin")
        destinationDirectory.set(outDir)
        from(jvmJar) { rename { "plugin.jar" } }
        from(layout.projectDirectory.file("tessera-plugin.json"))
        if (dex != null) {
          dependsOn(dex)
          from(dexDir) { include("classes.dex") }
        }
      }
    }
}

private fun androidSdkDir(root: File): File? {
  val props = File(root, "local.properties")
  val fromProps =
    if (props.isFile)
      Properties().apply { props.inputStream().use(::load) }.getProperty("sdk.dir")
    else null
  return listOfNotNull(fromProps, System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
    .map(::File)
    .firstOrNull { it.isDirectory }
}

private fun newestVersionDir(dir: File): File? =
  dir.listFiles()
    .orEmpty()
    .filter { it.isDirectory }
    .maxWithOrNull(
      compareBy<File>(
        { f ->
          f.name.filter { it.isDigit() || it == '.' }.split('.').getOrNull(0)?.toIntOrNull()
            ?: -1
        },
        { f ->
          f.name.filter { it.isDigit() || it == '.' }.split('.').getOrNull(1)?.toIntOrNull()
            ?: -1
        },
        { f ->
          f.name.filter { it.isDigit() || it == '.' }.split('.').getOrNull(2)?.toIntOrNull()
            ?: -1
        },
      ),
    )
