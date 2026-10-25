plugins {
  `kotlin-dsl`
}

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
  plugins {
    register("berillPlugin") {
      id = "berill.plugin"
      implementationClass = "BerillPluginConvention"
    }
    register("berillExternalPlugin") {
      id = "berill.external-plugin"
      implementationClass = "BerillExternalPluginConvention"
    }
  }
}
