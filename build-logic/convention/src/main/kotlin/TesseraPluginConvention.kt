import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private val AUTO_GROUPS =
  listOf("org.jetbrains.kotlin", "org.jetbrains.compose", "androidx.compose")

class TesseraPluginConvention : Plugin<Project> {
  override fun apply(project: Project) =
    with(project) {
      with(pluginManager) {
        apply("org.jetbrains.kotlin.multiplatform")
        apply("com.android.kotlin.multiplatform.library")
        apply("org.jetbrains.compose")
        apply("org.jetbrains.kotlin.plugin.compose")
        apply("org.jetbrains.kotlin.plugin.serialization")
      }
      val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
      val pluginName = name.replace('-', '_')

      extensions.configure<KotlinMultiplatformExtension> {
        jvm()

        (this as ExtensionAware).extensions
          .configure(KotlinMultiplatformAndroidLibraryTarget::class.java) {
            namespace = "dev.akkraizen.tessera.plugins.$pluginName"
            compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
          }

        sourceSets.getByName("commonMain").dependencies { implementation(project(":core-api")) }
        sourceSets.getByName("commonTest").dependencies {
          implementation(libs.findLibrary("kotlin-test").get())
        }
      }

      val srcRoot = layout.projectDirectory.dir("src")
      val verify =
        tasks.register("verifyPluginSources", VerifyPluginSourcesTask::class.java) {
          group = "verification"
          description = "Verifies that the plugin only uses core-api"
          srcDir.set(srcRoot)
          this.pluginName.set(project.name)
          sources.from(fileTree(srcRoot) { include("**/*.kt") })
          sourceSetNames.set(
            provider {
              srcRoot.asFile
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .joinToString(",") { it.name }
            },
          )
          report.set(layout.buildDirectory.file("reports/plugin-isolation.txt"))
        }
      tasks
        .matching {
          it.name.startsWith("compile")
            && it.name.contains("Kotlin")
        }
        .configureEach { dependsOn(verify) }

      afterEvaluate {
        val bad = configurations
          .filter { configuration ->
            val name = configuration.name
            isBadConfigurationName(name)
          }
          .flatMap { configuration -> configuration.dependencies.map { configuration.name to it } }
          .filterNot { (_, dependency) -> dependency is ProjectDependency && dependency.path == ":core-api" }
          .filterNot { (_, dependency) -> AUTO_GROUPS.any { g -> dependency.group?.startsWith(g) == true } }
        if (bad.isNotEmpty()) {
          throw GradleException(
            "Plugin `$name` can only depend on :core-api, but declared:\n" +
              bad.joinToString("\n") { (c, d) -> "  • $c: ${d.group ?: ""}:${d.name}" },
          )
        }
      }
    }

  private fun isBadConfigurationName(name: String): Boolean =
    !name.contains("Test", ignoreCase = true)
      && listOf("commonMain", "jvmMain", "androidMain")
        .any { name.startsWith(it) }
      && listOf("Implementation", "Api", "CompileOnly", "RuntimeOnly")
        .any { name.endsWith(it) }
}
