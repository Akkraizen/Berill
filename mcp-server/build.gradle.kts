import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinSerialization)
  application
}

application {
  mainClass = "dev.akkraizen.berill.mcp.MainKt"
  applicationName = "berill-mcp"
}

dependencies {
  implementation(project(":app:shared"))
  implementation(project(":mcp"))
  implementation(libs.sqldelight.sqliteDriver)
  implementation(libs.kotlinx.coroutinesCore)
  implementation(libs.kotlinx.serializationJson)
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.mcp.client)
}

distributions {
  main {
    contents {
      val shims = configurations.runtimeClasspath.flatMap { classpath ->
        classpath.incoming.artifacts.resolvedArtifacts.map { artifacts ->
          artifacts
            .groupBy { it.file.name }
            .values
            .filter { it.size > 1 }
            .flatten()
            .filter { (it.id.componentIdentifier as? ModuleComponentIdentifier)?.group?.startsWith("org.jetbrains.compose") == true }
            .map { it.file }
            .toSet()
        }
      }
      exclude { it.file in shims.get() }
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
  }
}
