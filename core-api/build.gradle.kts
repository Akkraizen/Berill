import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
  jvm()

  android {
    namespace = "dev.akkraizen.berill.api"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":plugin-sdk"))
      api(libs.compose.runtime)
      api(libs.compose.foundation)
      api(libs.compose.ui)
      api(libs.compose.material3)
      api(libs.kotlinx.coroutinesCore)
      api(libs.kotlinx.serializationJson)
      api(libs.kotlinx.datetime)
      implementation(libs.coil.compose)
      api(libs.compose.materialIconsExtended)
    }
  }
}
