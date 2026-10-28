import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  jvm()

  android {
    namespace = "dev.akkraizen.berill.app.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core"))
      implementation(project(":plugins:habits"))
      implementation(project(":plugins:planner"))
      implementation(project(":plugins:books"))
      implementation(project(":plugins:watching"))
      implementation(project(":plugins:finance"))
      implementation(project(":plugins:recipes"))
      implementation(project(":plugins:languages"))
      implementation(project(":plugins:study"))
      implementation(project(":plugins:projects"))
      implementation(project(":plugins:workouts"))
      implementation(project(":plugins:nutrition"))
    }
    jvmTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.sqldelight.sqliteDriver)
    }
  }
}
