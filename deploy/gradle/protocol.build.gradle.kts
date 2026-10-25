plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.serializationJson)
      implementation(libs.atomicfu)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
