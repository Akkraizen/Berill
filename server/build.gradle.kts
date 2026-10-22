plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlinSerialization)
}

group = "dev.akkraizen.tessera"

version = "1.0.0"

application {
  mainClass = "dev.akkraizen.tessera.server.ApplicationKt"
}

ktor {
  fatJar {
    archiveFileName.set("tessera-server.jar")
  }
}

dependencies {
  api(project(":protocol"))
  implementation(libs.logback)
  implementation(libs.ktor.serverCore)
  implementation(libs.ktor.serverNetty)
  implementation(libs.ktor.serverContentNegotiation)
  implementation(libs.ktor.serverAuth)
  implementation(libs.ktor.serverCallLogging)
  implementation(libs.ktor.serverStatusPages)
  implementation(libs.ktor.serializationJson)
  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.postgresql)
  implementation(libs.hikari)
  testImplementation(libs.ktor.serverTestHost)
  testImplementation(libs.kotlin.testJunit)
}
