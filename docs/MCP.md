# MCP server

Berill gives AI assistants such as Claude Desktop and Claude Code access to your data through the
[Model Context Protocol](https://modelcontextprotocol.io). There are two ways to run it:

- **Built into the desktop app** (Windows and macOS). The app serves MCP from its own database, so
  there is nothing to install, sign in to, or keep in sync. This is the recommended option.
- **Standalone `berill-mcp`**. A command-line app that works as one more Berill device: it keeps its
  own copy of the data, syncs it with your sync server, and decrypts end-to-end encrypted records
  locally. Use it where the desktop app does not run, for example on a computer without a display.

Both offer the same [tools](#tools).

## Contents

- [Built into the desktop app](#built-into-the-desktop-app)
- [Standalone server](#standalone-server)
- [Tools](#tools)
- [Installation](#installation)
- [Connecting a client](#connecting-a-client)
- [Commands](#commands)
- [Security](#security)
- [Related documents](#related-documents)

## Built into the desktop app

Open **Settings → AI assistants (MCP)** and turn on the server. It is off by default.

| Setting | Effect |
| --- | --- |
| Turn on the MCP server | Starts the server on `http://127.0.0.1:39871/mcp`. If the port is busy, another free port is used and remembered. |
| Allow changes | Offers the note-writing tools. Without it, assistants can only read. |
| Access token | Required by every HTTP request as `Authorization: Bearer <token>`. Changing it disconnects HTTP clients that use the old one. |

The settings page shows ready-to-copy configuration for Claude Desktop and Claude Code.

```text
Claude Desktop ──stdio──► Berill --mcp ──HTTP + token──► running Berill app
Claude Code ─────────────────────────────HTTP + token──►        │
                                                                └─ the app's own database
```

Claude Desktop starts local servers over stdio, so the installed app doubles as a bridge: launched as
`Berill --mcp`, it does not open a window, finds the running app through `~/.berill/mcp-endpoint.json`,
reads the token from the app's secure storage, and forwards messages. If the app is not running, the
bridge starts it in the background and waits up to 30 seconds. If the server is turned off, requests
fail with a message asking to turn it on.

```json
{
  "mcpServers": {
    "berill": {
      "command": "C:\\Program Files\\Berill\\Berill.exe",
      "args": ["--mcp"]
    }
  }
}
```

On macOS the command is `/Applications/Berill.app/Contents/MacOS/Berill`. Claude Code can use the same
bridge (`claude mcp add berill -- <path> --mcp`) or connect over HTTP directly:

```sh
claude mcp add --transport http berill http://127.0.0.1:39871/mcp --header "Authorization: Bearer <token>"
```

When the app runs from Gradle rather than an installer, there is no launcher to point at, and the
settings page offers only the HTTP configuration.

The server accepts connections only from this computer, checks the token in constant time, and
rejects requests whose `Host` or `Origin` is not local. Changes made by an assistant go through the
app's normal storage and sync to your other devices like any edit.

## Standalone server

### How it works

```text
Claude Desktop / Claude Code ──stdio──► berill-mcp
                                          │  local SQLite copy (~/.berill-mcp)
                                          └──HTTPS /api/sync──► sync server (its own token)
```

| Part | Location | Purpose |
| --- | --- | --- |
| `mcp` | Gradle module | The tools, the HTTP server used by the desktop app, and the stdio bridge. |
| `mcp-server` | Gradle module | The command-line app: Berill core without UI, built-in sections, Kotlin MCP SDK over stdio. |
| `~/.berill-mcp/berill.db` | Your computer | Local copy of the data. |
| `~/.berill-mcp/secrets` | Your computer | Sync token and encryption key, protected like on the desktop app (DPAPI on Windows, keychain on macOS). |
| `~/.berill-mcp/logs` | Your computer | Log files. Nothing is written to stdout except protocol messages. |

The sync server only sees encrypted fields when end-to-end encryption is on, so the MCP server must
run where the encryption password can be entered: on your computer, not next to the sync server.
Changes made through MCP go through the same hybrid logical clocks as edits on other devices and
merge with them.

### Tools

| Tool | Access | Purpose |
| --- | --- | --- |
| `search` | read | Full-text search across notes and every section. |
| `list_collections` | read | Collections, their sections, and record counts. |
| `read_records` | read | Records of one collection, paged. |
| `get_record` | read | One record by collection and id. |
| `list_notes` | read | Notes, newest first, with previews. |
| `read_note` | read | Full Markdown text of a note. |
| `sync_status` | read | Sync configuration, last run, pending changes. |
| `sync_now` | network | Syncs immediately. |
| `create_note` | write | Creates a note. |
| `append_to_note` | write | Adds text to the end of a note. |
| `update_note` | write | Replaces the title and/or the body of a note. |
| `delete_note` | destructive | Deletes a note on every device; requires `confirm: true`. |

With `--read-only`, the write tools are not offered at all. Section data other than notes is read-only
in this version.

### Installation

Requires Java 21.

```sh
./gradlew :mcp-server:installDist
```

The app is written to `mcp-server/build/install/berill-mcp`; the launcher is `bin/berill-mcp`
(`bin\berill-mcp.bat` on Windows). The folder can be moved anywhere.

Create a separate token for the MCP server on the sync server, so it shows up as its own device and
can be revoked on its own:

```sh
docker compose run --rm sync token create <user> --name mcp
```

Then sign in once:

```sh
berill-mcp login --server https://sync.berill.space
```

The command asks for the token and, if end-to-end encryption is on, for the encryption password. In
scripts, pass them through `BERILL_TOKEN` and `BERILL_E2E_PASSWORD`.

### Connecting a client

Claude Desktop (`claude_desktop_config.json`) or any client that starts stdio servers:

```json
{
  "mcpServers": {
    "berill": {
      "command": "C:\\Tools\\berill-mcp\\bin\\berill-mcp.bat",
      "args": ["--read-only"]
    }
  }
}
```

Remove `--read-only` to allow note editing. Claude Code:

```sh
claude mcp add berill -- /path/to/berill-mcp/bin/berill-mcp
```

### Commands

| Command | Purpose |
| --- | --- |
| `berill-mcp [serve] [--read-only]` | Runs the MCP server over stdio. Syncs in the background while running and once more on exit. |
| `berill-mcp login --server URL [--token TOKEN]` | Connects to a sync server, downloads the data, unlocks encryption. |
| `berill-mcp status` | Shows the server, user, record count, and encryption state. |
| `berill-mcp logout` | Removes the token and the encryption key; local data stays. |

Every command accepts `--data DIR` to use a folder other than `~/.berill-mcp`.

### Security

- The assistant gets the same access to your data as any of your devices. Start with `--read-only`.
- `delete_note` refuses to run without `confirm: true`, and its description tells the assistant to ask
  you first.
- Revoke the MCP token on the sync server to cut off access without touching other devices.
- External plugins are never loaded by the MCP server.

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [DEPLOY.md](DEPLOY.md) — server setup, deployment, and maintenance.
- [RELEASES.md](RELEASES.md) — versioning, signing, and building releases.
