import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlinSerialization)
}

group = "dev.akkraizen.berill"

application {
  mainClass = "dev.akkraizen.berill.server.ApplicationKt"
}

ktor {
  fatJar {
    archiveFileName.set("berill-server.jar")
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

val generateServerVersion = tasks.register("generateServerVersion") {
  val versionFile = rootProject.file("version.properties")
  val outDir = layout.buildDirectory.dir("generated/version-resource")
  inputs.files(versionFile).optional()
  outputs.dir(outDir)
  doLast {
    val props = Properties()
    if (versionFile.isFile) versionFile.inputStream().use { props.load(it) }
    val out = outDir.get().asFile
    out.mkdirs()
    File(out, "berill-server-version.txt").writeText(props.getProperty("versionName") ?: "dev")
  }
}
sourceSets.main {
  resources.srcDir(generateServerVersion)
}
