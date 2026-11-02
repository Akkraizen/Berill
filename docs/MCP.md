# MCP server

Berill gives AI assistants access to your data through the
[Model Context Protocol](https://modelcontextprotocol.io). It speaks the standard STDIO and Streamable
HTTP transports, so it works with any compatible client: Claude Desktop, Claude Code, OpenAI Codex,
Cursor, VS Code (GitHub Copilot), Windsurf, Gemini CLI, Zed, opencode, Cline / Roo Code, JetBrains AI
Assistant and others. There are two ways to run it:

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
- [Supported clients](#supported-clients)
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

The settings page has a picker of assistant apps and shows ready-to-copy configuration in the format
each of them expects; see [Supported clients](#supported-clients).

```text
any STDIO client ──stdio──► Berill --mcp ──HTTP + token──► running Berill app
any HTTP client ───────────────────────────HTTP + token──►        │
                                                                  └─ the app's own database
```

Most clients start local servers over stdio, so the installed app doubles as a bridge: launched as
`Berill --mcp`, it does not open a window, finds the running app through `~/.berill/mcp-endpoint.json`,
reads the token from the app's secure storage, and forwards messages. If the app is not running, the
bridge starts it in the background and waits up to 30 seconds. If the server is turned off, requests
fail with a message asking to turn it on. Requests are forwarded in parallel, so a slow tool call does
not hold up pings, cancellations or parallel tool calls, and an expired session is re-established
transparently.

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
bridge (`claude mcp add --scope user berill -- <path> --mcp`) or connect over HTTP directly:

```sh
claude mcp add --scope user --transport http berill http://127.0.0.1:39871/mcp --header "Authorization: Bearer <token>"
```

When the app runs from Gradle rather than an installer, there is no launcher to point at, and the
settings page offers HTTP configurations only. Claude Desktop cannot send HTTP headers itself, so for
it the page wraps the endpoint in [`mcp-remote`](https://www.npmjs.com/package/mcp-remote) (requires
Node.js).

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
| `search` | read | Full-text search across notes and every enabled section. |
| `list_collections` | read | Collections, their sections, record counts, schemas and whether they are writable. |
| `read_records` | read | Records of one collection, paged. |
| `get_record` | read | One record by collection and id, with the `expected` snapshot used for edits. |
| `list_notes` | read | Notes, newest first, with previews. |
| `read_note` | read | Full Markdown text of a note. |
| `sync_status` | read | Sync configuration, last run, pending changes. |
| `sync_now` | network | Syncs immediately. |
| `create_note` | write | Creates a note. |
| `append_to_note` | write | Adds text to the end of a note. |
| `update_note` | write | Replaces the title and/or the body of a note; requires the `expected` snapshot. |
| `delete_note` | destructive | Deletes a note on every device; requires `expected` and `confirm: true`. |
| `create_record` | write | Creates a record in a writable collection, validated by the owning module. |
| `update_record` | write | Changes fields of a record; requires the `expected` snapshot. |
| `delete_record` | destructive | Moves a record to trash; requires `expected` and `confirm: true`. |
| `berill_<module>_read` | read | `list` / `get` for the collections of one enabled module, e.g. `berill_health_read`. |
| `berill_<module>_edit` | write | `create` / `update` / `delete` for the writable collections of one module. |

With `--read-only` (or with **Allow changes** off in the app), the write tools are not offered at
all. Module tools appear and disappear as modules are enabled or disabled; clients are notified with
`notifications/tools/list_changed`. Results are returned both as text and as `structuredContent`.

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

Print the configuration for your client; the launcher path is filled in automatically:

```sh
berill-mcp config --client cursor
berill-mcp config --client codex --read-only
berill-mcp config            # every supported client
```

On Windows the launcher is a `.bat` file, which Node-based clients cannot start directly, so the
printed configuration runs it through `cmd /c`. See [Supported clients](#supported-clients) for the
list of names and where each client keeps its settings.

### Commands

| Command | Purpose |
| --- | --- |
| `berill-mcp [serve] [--read-only]` | Runs the MCP server over stdio. Syncs in the background while running and once more on exit. |
| `berill-mcp login --server URL [--token TOKEN]` | Connects to a sync server, downloads the data, unlocks encryption. |
| `berill-mcp status` | Shows the server, user, record count, and encryption state. |
| `berill-mcp logout` | Removes the token and the encryption key; local data stays. |
| `berill-mcp config [--client NAME] [--read-only]` | Prints ready-made configuration for MCP clients. |

Every command accepts `--data DIR` to use a folder other than `~/.berill-mcp`.

### Security

- The assistant gets the same access to your data as any of your devices. Start with `--read-only`.
- `delete_note` refuses to run without `confirm: true`, and its description tells the assistant to ask
  you first.
- Revoke the MCP token on the sync server to cut off access without touching other devices.
- External plugins are never loaded by the MCP server.

## Supported clients

The desktop settings page and `berill-mcp config` generate the configuration for each client in its
own format. The STDIO bridge is preferred everywhere: the token never ends up in a client's settings,
and Berill is started on demand.

| Client | `--client` | Where the configuration goes | Format |
| --- | --- | --- | --- |
| Claude Desktop | `claude-desktop` | `claude_desktop_config.json` | `mcpServers` → `command`/`args`; HTTP via `mcp-remote` |
| Claude Code | `claude-code` | terminal | `claude mcp add --scope user …` |
| OpenAI Codex | `codex` | `~/.codex/config.toml` | `[mcp_servers.berill]` with `command`/`args` or `url`/`http_headers` |
| Cursor | `cursor` | `~/.cursor/mcp.json` | `mcpServers` → `command`/`args` or `url`/`headers` |
| VS Code (GitHub Copilot) | `vscode` | `.vscode/mcp.json` or the user profile | `servers` → `type: stdio` / `type: http` |
| Windsurf | `windsurf` | `~/.codeium/windsurf/mcp_config.json` | `mcpServers` → `command`/`args` or `serverUrl`/`headers` |
| Gemini CLI | `gemini` | `~/.gemini/settings.json` | `mcpServers` → `command`/`args` or `httpUrl`/`headers` |
| Zed | `zed` | `settings.json` | `context_servers` → `source: custom`, `command`/`args` or `url`/`headers` |
| opencode | `opencode` | `opencode.json` | `mcp` → `type: local` with a command array, or `type: remote` |
| Cline / Roo Code | `cline` | `cline_mcp_settings.json` | `mcpServers` → `command`/`args` or `type: streamableHttp` |
| JetBrains AI Assistant / Junie | `jetbrains` | Settings → Tools → AI Assistant → Model Context Protocol | `mcpServers` JSON |
| Anything else | `generic` | client-specific | `mcpServers` → `command`/`args` or `type: http` |

Example for OpenAI Codex with the desktop app on Windows:

```toml
[mcp_servers.berill]
command = "C:\\Program Files\\Berill\\Berill.exe"
args = ["--mcp"]
```

Example for VS Code over Streamable HTTP:

```json
{
  "servers": {
    "berill": {
      "type": "http",
      "url": "http://127.0.0.1:39871/mcp",
      "headers": { "Authorization": "Bearer <token>" }
    }
  }
}
```

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [DEPLOY.md](DEPLOY.md) — server setup, deployment, and maintenance.
- [RELEASES.md](RELEASES.md) — versioning, signing, and building releases.
