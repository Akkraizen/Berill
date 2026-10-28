plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinSerialization)
}

dependencies {
  api(project(":core"))
  api(libs.mcp.server)
  implementation(libs.ktor.serverCio)
  implementation(libs.kotlinx.coroutinesCore)
  implementation(libs.kotlinx.serializationJson)
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.mcp.client)
  testImplementation(libs.sqldelight.sqliteDriver)
}
