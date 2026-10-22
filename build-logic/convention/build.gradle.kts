plugins {
  `kotlin-dsl`
}

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
  plugins {
    register("tesseraPlugin") {
      id = "tessera.plugin"
      implementationClass = "TesseraPluginConvention"
    }
    register("tesseraExternalPlugin") {
      id = "tessera.external-plugin"
      implementationClass = "TesseraExternalPluginConvention"
    }
  }
}
