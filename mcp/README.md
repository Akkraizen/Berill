# Berill MCP

Berill exposes the same tools to any compatible MCP client. Use either STDIO (`Berill --mcp`) or the authenticated loopback Streamable HTTP endpoint shown in Settings → AI assistants (MCP). Copy the generated connection configuration; no vendor-specific CLI is required. The installed SDK negotiates the supported protocol version with the client.

The HTTP endpoint requires `Authorization: Bearer <TOKEN>` and rejects untrusted origins. The STDIO bridge obtains the endpoint and credential from the running local application, forwards the negotiated protocol version and server notifications, and never automatically repeats an ambiguously completed request. It can start Berill in the background.

## Discovery and editing

1. Call `list_collections` to discover enabled collections, their schemas, and `writable` status.
2. Read using `get_record` or a `berill_<plugin>_read` tool.
3. Pass the returned **expected** object unchanged to `update_record` / `berill_<plugin>_edit`, together with only the fields being changed.
4. If the server reports a conflict, read the record again; never blindly retry an edit. Creating a record is not automatically retried by the bridge.
5. Deletion requires writable mode, the expected snapshot and `confirm=true`, and follows the owning module's lifecycle (for example, Google Calendar deletion is queued for Google sync).

Writes are absent from discovery in read-only mode. Disabled plugins disappear from tool discovery, and old handlers also verify that their owner is still enabled. Settings, secrets and authentication records are never exposed as collections.

Plugin-owned adapters live in `plugins/<name>/.../mcp/`. They supply serializers, schemas, validation and lifecycle rules via the SDK-independent `McpSource` / `McpCollection` API. The MCP module handles protocol transport and routing; it has no dependency on concrete plugin modules. Derived/imported collections are explicitly read-only, while normal records have typed mutations. Habit progress uses a `{date, values}` view, with date-based IDs.

Notes belong to core and their adapter is in `core/.../notes/NoteMcp.kt`. Existing note and sync tool names remain available. Use expected snapshots for replacements; append operations append to the current text atomically. Before an MCP mutation, the open note editor flushes pending changes on the UI thread, so a stale agent snapshot is rejected. Concurrent changes to different note fields merge; overlapping unsaved text is preserved in the conflict journal for explicit resolution.

Protocol background: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports
