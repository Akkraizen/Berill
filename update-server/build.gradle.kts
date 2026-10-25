import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlinSerialization)
}

group = "dev.akkraizen.berill"

application {
  mainClass = "dev.akkraizen.berill.updates.ApplicationKt"
}

ktor {
  fatJar {
    archiveFileName.set("berill-updates.jar")
  }
}

dependencies {
  implementation(project(":protocol"))
  implementation(libs.logback)
  implementation(libs.ktor.serverCore)
  implementation(libs.ktor.serverNetty)
  implementation(libs.ktor.serverContentNegotiation)
  implementation(libs.ktor.serverCallLogging)
  implementation(libs.ktor.serverStatusPages)
  implementation(libs.ktor.serializationJson)
  testImplementation(libs.ktor.serverTestHost)
  testImplementation(libs.kotlin.testJunit)
}

val generateUpdatesVersion = tasks.register("generateUpdatesVersion") {
  val versionFile = rootProject.file("version.properties")
  val outDir = layout.buildDirectory.dir("generated/version-resource")
  inputs.files(versionFile).optional()
  outputs.dir(outDir)
  doLast {
    val props = Properties()
    if (versionFile.isFile) versionFile.inputStream().use { props.load(it) }
    val out = outDir.get().asFile
    out.mkdirs()
    File(out, "berill-updates-version.txt").writeText(props.getProperty("versionName") ?: "dev")
  }
}
sourceSets.main {
  resources.srcDir(generateUpdatesVersion)
}
