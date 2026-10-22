package dev.akkraizen.tessera.server

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun runTokenCli(args: List<String>): Int {
  val command = args.firstOrNull()
  if (command == null || command == "help" || command == "--help") {
    printUsage()
    return if (command == null) 2 else 0
  }
  val store = TokenRepository(ServerDatabase.fromEnv())
  return when (command) {
    "create" -> {
      val user = args.getOrNull(1)
        ?.takeUnless { it.startsWith("--") }
        ?: return usageError("specify a user")
      val name = option(args, "--name") ?: "device"

      if (!TokenCodec.USER.matches(user))
        return usageError("user name: letters, digits, _ . - (up to 64 characters)")
      val (info, token) = store.create(user, name)
      println(
        """
                |Token for `${info.user}` (${info.name}), id ${info.id}:
                |
                |    $token
                |
                |Copy it now — the server stores only its hash and cannot show the token again.
                |In the app: Settings → Sync → `Access token`.
                |Revoke: token revoke ${info.id}
                """
          .trimMargin(),
      )
      0
    }

    "list" -> {
      val tokens = store.list(args.getOrNull(1))
      if (tokens.isEmpty()) {
        println("No tokens found.")
      } else {
        println(row("ID", "USER", "DEVICE", "CREATED", "LAST USED", "STATUS"))
        tokens.forEach { t ->
          println(
            row(
              t.id,
              t.user,
              t.name,
              date(t.createdAt),
              t.lastUsedAt?.let(::date) ?: "—",
              if (t.revokedAt != null) "revoked ${date(t.revokedAt)}" else "active",
            ),
          )
        }
      }
      0
    }

    "revoke" -> {
      val id = args.getOrNull(1) ?: return usageError("specify a token id (token list)")
      if (store.revoke(id)) {
        println("Token $id revoked; devices using it will no longer sync.")
        0
      } else {
        System.err.println("No active token found with id $id.")
        1
      }
    }

    else -> usageError("unknown command `$command`")
  }
}

private fun option(
  args: List<String>,
  name: String,
): String? {
  val i = args.indexOf(name)
  return if (i >= 0) args.getOrNull(i + 1) else null
}

private val dateFormat =
  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun date(millis: Long): String = dateFormat.format(Instant.ofEpochMilli(millis))

private fun row(vararg cells: String): String {
  val widths = intArrayOf(10, 14, 16, 18, 18, 0)
  return cells
    .mapIndexed { i, c ->
      if (widths[i] > 0)
        c.take(widths[i] - 1).padEnd(widths[i])
      else
        c
    }
    .joinToString("")
}

private fun usageError(message: String): Int {
  System.err.println("Error: $message")
  printUsage()
  return 2
}

private fun printUsage() {
  println(
    """
        |Tessera sync tokens:
        |  token create <user> [--name <device>]   create a new token
        |  token list [<user>]                    list tokens
        |  token revoke <id>                      revoke a token
        """
      .trimMargin(),
  )
}
