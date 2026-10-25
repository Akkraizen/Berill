import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

object PluginSourceRules {
  val allowedSourceSets = setOf("commonMain", "commonTest")

  val forbidden: List<Pair<Regex, String>> = listOf(
    Regex("""\b(java|javax|android|sun|jdk)\.[a-z]""") to
      "Java/Android platform APIs (files, network, processes, reflection)",
    Regex("""\bkotlin\.(io\.(path|File|readBytes|writeText)|concurrent|system|reflect\.(full|jvm))""") to
      "files, threads, process termination, or full Kotlin reflection",
    Regex("""\b(io\.ktor|okhttp3|coil3)\.""") to
      "network and image loading — use ctx.http and Cover instead",
    Regex("""\bdev\.akkraizen\.berill\.(core|protocol|sdk)\b""") to
      "internal app modules — plugin can only access core-api",
    Regex("""\bLocalUriHandler\b""") to
      "opening links — use rememberOpenLink() from core-api",
    Regex("""\bLocalClipboard(Manager)?\b""") to "clipboard access",
    Regex("""\bnewSingleThreadContext\b|\bnewFixedThreadPoolContext\b""") to
      "custom thread pools"
  )

  fun check(text: String): List<Pair<Int, String>> {
    val problems = mutableListOf<Pair<Int, String>>()
    var inBlockComment = false
    text.lines().forEachIndexed { i, raw ->
      var line = raw
      if (inBlockComment) {
        val end = line.indexOf("*/")
        if (end < 0) return@forEachIndexed
        line = line.substring(end + 2)
        inBlockComment = false
      }
      val start = line.indexOf("/*")
      if (start >= 0 && line.indexOf("*/", start) < 0) {
        inBlockComment = true
        line = line.substring(0, start)
      }
      line = line.substringBefore("//").replace(Regex(""""(\\.|[^"\\])*""""), "\"\"")
      forbidden.forEach { (rule, why) ->
        if (rule.containsMatchIn(line)) problems += (i + 1) to why
      }
    }
    return problems
  }
}

@CacheableTask
abstract class VerifyPluginSourcesTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  @get:Internal
  abstract val srcDir: DirectoryProperty

  @get:Input
  abstract val sourceSetNames: Property<String>

  @get:Input
  abstract val pluginName: Property<String>

  @get:OutputFile
  abstract val report: RegularFileProperty

  @TaskAction
  fun verify() {
    val problems = mutableListOf<String>()
    val extra = sourceSetNames.get().split(',').filter {
      it.isNotBlank() && it !in PluginSourceRules.allowedSourceSets
    }
    if (extra.isNotEmpty()) {
      problems +=
        "src/${
          extra.joinToString(", src/")
        }: plugins only allow common code (commonMain) — platform code provides access to files, network, and processes"
    }
    val root = srcDir.get().asFile
    sources.files
      .filter { it.extension == "kt" }
      .sorted()
      .forEach { file ->
        PluginSourceRules.check(file.readText()).forEach { (line, why) ->
          problems += "${file.relativeTo(root)}:$line — $why"
        }
      }
    report.get().asFile.writeText(problems.joinToString("\n"))
    if (problems.isNotEmpty()) {
      throw GradleException(
        "Plugin `${pluginName.get()}` violates isolation rules:\n" +
          problems.joinToString("\t\n")
      )
    }
  }
}
