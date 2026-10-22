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

allprojects {
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
