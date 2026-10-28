import java.io.File

plugins {
  id("berill.plugin")
}

abstract class GenerateGoogleClient : DefaultTask() {
  @get:Input abstract val clientId: Property<String>
  @get:Input abstract val clientSecret: Property<String>
  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    fun q(s: String) = "\"" + s.trim().replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
    val text = """
      |package dev.akkraizen.berill.plugins.planner.data
      |
      |internal object GoogleClient {
      |  const val ID: String = ${q(clientId.get())}
      |  const val SECRET: String = ${q(clientSecret.get())}
      |}
      |""".trimMargin()
    val target = File(outputDirectory.get().asFile, "dev/akkraizen/berill/plugins/planner/data/GoogleClient.kt")
    target.parentFile.mkdirs()
    if (!target.isFile || target.readText() != text) target.writeText(text)
  }
}

val generateGoogleClient = tasks.register<GenerateGoogleClient>("generateGoogleClient") {
  group = "build"
  description = "Generates the Google OAuth client used by the planner calendar"
  clientId.set(
    providers.gradleProperty("berill.googleClientId")
      .orElse(providers.environmentVariable("BERILL_GOOGLE_CLIENT_ID"))
      .orElse("")
  )
  clientSecret.set(
    providers.gradleProperty("berill.googleClientSecret")
      .orElse(providers.environmentVariable("BERILL_GOOGLE_CLIENT_SECRET"))
      .orElse("")
  )
  outputDirectory.set(layout.buildDirectory.dir("generated/google"))
}

kotlin.sourceSets.named("commonMain") {
  kotlin.srcDir(generateGoogleClient.flatMap { it.outputDirectory })
}
