# MCP server

`berill-mcp` gives AI assistants such as Claude Desktop and Claude Code access to Berill data through
the [Model Context Protocol](https://modelcontextprotocol.io). It works as one more Berill device: it
keeps its own copy of the data, syncs it with your sync server, and decrypts end-to-end encrypted
records locally.

## Contents

- [How it works](#how-it-works)
- [Tools](#tools)
- [Installation](#installation)
- [Connecting a client](#connecting-a-client)
- [Commands](#commands)
- [Security](#security)
- [Related documents](#related-documents)

## How it works

```text
Claude Desktop / Claude Code ──stdio──► berill-mcp
                                          │  local SQLite copy (~/.berill-mcp)
                                          └──HTTPS /api/sync──► sync server (its own token)
```

| Part | Location | Purpose |
| --- | --- | --- |
| `mcp-server` | Gradle module | The command-line app: Berill core without UI, built-in sections, Kotlin MCP SDK over stdio. |
| `~/.berill-mcp/berill.db` | Your computer | Local copy of the data. |
| `~/.berill-mcp/secrets` | Your computer | Sync token and encryption key, protected like on the desktop app (DPAPI on Windows, keychain on macOS). |
| `~/.berill-mcp/logs` | Your computer | Log files. Nothing is written to stdout except protocol messages. |

The sync server only sees encrypted fields when end-to-end encryption is on, so the MCP server must
run where the encryption password can be entered: on your computer, not next to the sync server.
Changes made through MCP go through the same hybrid logical clocks as edits on other devices and
merge with them.

## Tools

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

## Installation

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

## Connecting a client

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

## Commands

| Command | Purpose |
| --- | --- |
| `berill-mcp [serve] [--read-only]` | Runs the MCP server over stdio. Syncs in the background while running and once more on exit. |
| `berill-mcp login --server URL [--token TOKEN]` | Connects to a sync server, downloads the data, unlocks encryption. |
| `berill-mcp status` | Shows the server, user, record count, and encryption state. |
| `berill-mcp logout` | Removes the token and the encryption key; local data stays. |

Every command accepts `--data DIR` to use a folder other than `~/.berill-mcp`.

## Security

- The assistant gets the same access to your data as any of your devices. Start with `--read-only`.
- `delete_note` refuses to run without `confirm: true`, and its description tells the assistant to ask
  you first.
- Revoke the MCP token on the sync server to cut off access without touching other devices.
- External plugins are never loaded by the MCP server.

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [DEPLOY.md](DEPLOY.md) — server setup, deployment, and maintenance.
- [RELEASES.md](RELEASES.md) — versioning, signing, and building releases.
