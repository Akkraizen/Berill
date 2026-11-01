# Changelog

All notable changes to Berill are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Versions refer to the client
applications (`versionName` in `version.properties`); the sync server, update server, and MCP server
are released together with them.

## [Unreleased]

### Added

#### Connected workspace

- Global search and a command palette across enabled modules, available with Ctrl+K / Command+K.
- A daily overview combining habits, workouts, nutrition, weight, expenses, planner items and a daily note.
- Note backlinks, a trash view with restore and confirmed permanent deletion, and local ZIP export/import
  containing JSON records, Markdown notes and attachments without server credentials.
- Home page configuration for cards and quick actions; compact quick-entry dialogs open over Home,
  with drafts, recent values, templates and save-and-add-another actions where supported.
- Configurable keyboard shortcuts and note editor preferences for line numbers, font size and monospace text.
- Note focus mode with a centered editor, shortcut and Escape exit, preserving drafts and editor state.
- A notification center, shared reminder scheduling, per-module notification controls, sound preferences
  and quiet hours that can span midnight.
- A synchronization conflict viewer with preserved versions and field-level resolution.

#### Module capabilities and Android integration

- Independent SearchSource, DaySummarySource and MCP capabilities supplied by plugin modules.
- Typed, validated MCP create/update/delete operations, optimistic concurrency checks and read-only mode;
  tool availability follows enabled plugins. Generic STDIO and authenticated local HTTP configurations.
- Android text/link sharing, Quick Settings actions for water, habits and expenses, and widget previews.
- Opt-in Health Connect imports for weight, steps and sleep, with Android permission handling.
- SM-2 spaced repetition for language cards, recurring financial operations and category budgets.
- Workout rest notifications and suggested weights from previous completed workouts.

#### Storage and deployment

- Content-addressed attachment files with lazy downloads, per-user server isolation and client-side
  encryption when end-to-end encryption is enabled; legacy recipe photos migrate from inline base64.
- Optional private S3-compatible attachment storage with endpoint, region and prefix configuration,
  signed requests, bounded timeouts and continued access to existing local attachments.
- PostgreSQL and attachment backups, including S3 attachment mirroring, with optional independent
  encrypted restic storage, explicit repository initialization and documented restore/rollback steps.
  External buckets and credentials must be supplied by the operator; no cloud storage is provisioned.
- Linux desktop distributions and platform integration.


#### Platforms

- macOS support for the desktop app (macOS 12 or newer): menu bar icon instead of the system tray,
  Dock reopen and ⌘Q handling, login item autostart, a global shortcut, and Keychain storage for
  secrets.
- macOS DMG packaging, CI builds on macOS, and DMG downloads through the update server.
- Latest-release download links on the update server.

#### AI assistants (MCP)

- `berill-mcp`, a standalone MCP server for Claude Desktop, Claude Code, and other clients. It works
  as a separate sync device with its own local copy of the data, supports end-to-end encryption, and
  offers tools for search, reading collections and records, notes, and sync status. Note editing is
  available unless it runs with `--read-only`.
- An MCP server built into the desktop app (Windows and macOS). It serves the app's own database
  over Streamable HTTP on `127.0.0.1`, requires a bearer token, and rejects non-local `Host` and
  `Origin` headers. It is off by default and is managed in **Settings → AI assistants (MCP)**, with
  read-only and read-write modes, token management, and ready-to-copy configuration for compatible MCP clients.
- `Berill --mcp`, a stdio bridge for clients that only start local servers. It connects to the
  running app, starts it in the background when needed, and reconnects after the server restarts.

#### Planner and calendar

- A **Calendar** page in the planner with a month grid and an hour-by-hour day view that shows
  events, timed tasks, all-day events, and deadlines from other sections.
- Two-way Google Calendar sync with one-click sign-in (OAuth with PKCE through the system browser).
- Recurring events: daily, weekly on selected days, monthly, and yearly, with an end date and
  per-occurrence exceptions ("This one only" when editing or deleting).
- Birthdays as yearly all-day events that show the person's age.
- Date and time pickers next to date and time fields.

#### Android home screen widgets

- 22 widgets built with Jetpack Glance, each listed separately in the launcher's widget picker:
  - Day summary across all modules.
  - Quick note, pinned note with checkable checklist items, and a daily journal with mood buttons.
  - Today's habits with check-off, and a 7-day habit grid with streaks.
  - Today's plan with checkable tasks and events, a month calendar with event markers and month
    switching, and a two-week schedule.
  - Next workout with a Start button, and a rest timer.
  - Calories and macronutrients, water with "+glass" buttons, and a weight chart.
  - Books being read, titles being watched with "+1 episode", and a dictionary word card.
  - Nearest deadlines, monthly finances, quick expense entry by category, and a recipe of the day.
  - "Berill: any widget", which can show any available widget, including ones contributed by
    modules.
- Widgets follow the app's accent color, open the matching screen on tap, and refresh on local
  edits, after sync, every 30 minutes, and when a timer ends.
- A platform-neutral widget model in `core-api` (`BerillPlugin.widgets()`, serializable content and
  actions) and a widget hub in `core` that can export JSON snapshots, prepared for future Windows,
  iOS, and macOS widgets. See [docs/WIDGETS.md](docs/WIDGETS.md).

#### Interface and plugin API

- Sections with several pages expand into their subpages in the drawer and the sidebar.
- The startup screen uses the selected theme instead of the default one.
- Adaptive rows for form fields and compact statistic tiles.
- HTTP requests and OAuth redirects in the plugin API, limited to the hosts each plugin declares.
- A route for opening the new-expense dialog with a preselected category in Finance.

### Changed

- Search, daily overview, trash and workspace dialogs use Material 3 Expressive styling; oversized
  cards and quick-entry dialogs are smaller, and long secondary lists scroll within bounded areas.
- Desktop search is centered in an adaptive title bar. Mobile header actions move into an overflow
  menu so titles remain readable. The desktop synchronization icon has a fixed position beside the logo.
- Synchronization state is shown in the header/menu instead of routine notification-center events.
- Disabled plugins stop contributing capabilities, settings and reminders while their data is retained.
- User-facing Russian literals use localization resources; persisted enum migrations accept legacy values.
- The sync server Docker image builds and tests its source in a multistage build.
- Floating Windows windows have rounded corners; maximized and fullscreen windows have square corners.
- Plugin sources are split into `data`, `ui`, and `i18n` packages.
- MCP tools moved into a shared `mcp` module used by both `berill-mcp` and the desktop app.
- The desktop runtime image now includes the `java.net.http` module.

### Fixed

- Cached recipe photos load without waiting for network uploads. Attachment reads run off the UI thread, use a bounded 24 MiB memory cache backed by persistent local files, retry temporary failures, and expose image decoding errors.
- Windows installer build, publication and update discovery consistently use `-windows.exe`; legacy `-Setup.exe` downloads remain supported.
- Sync deployment transfers the backup Dockerfile and builds its local image before recreating containers.
- Release widget tests respect the selected language on English CI runners; failed Windows test reports are retained as workflow artifacts.
- Cross-module synchronization recovery, logical clocks and reconciliation after server/dataset changes.
- Unsaved note drafts are protected against concurrent synchronization and MCP edits, with conflicts retained.
- Rescheduled planner tasks remain planned and incomplete on the source day and planned on the destination day.
- API failures are reported consistently without silently dropping failed operations.
- Windows Hot Reload uses separate startup libraries per launch to avoid deleting JARs locked by a previous JVM.
- Header action hover/press feedback, icon sizes and colors, stale highlighting after popup dismissal,
  dropdown widths and unwanted progress-bar stop indicators.
- Remote backup failures preserve local backups; restic retention groups snapshots consistently even though
  dump filenames change, and does not mistake connection/authentication errors for an uninitialized repository.
- Plugin layouts on narrow screens.
- Alignment of the workout tiles on the home screen.
- The tray menu now opens at the cursor, and its labels stay on one line.
- Field labels and task times no longer wrap onto a second line.

## [1.0.0] - 2026-10-25

First public release.

### Added

#### Applications

- Android app (Android 8.0 / API 26 or newer) and Windows desktop app sharing Kotlin Multiplatform
  logic and a Compose Multiplatform interface.
- Local-first storage in SQLite; a server is only needed to synchronize devices.
- Russian and English localization; light, dark, and system themes with accent colors.
- Onboarding for language, appearance, and module selection.
- A home screen with cards and quick actions supplied by enabled modules.
- Markdown notes with live preview.
- On Windows: closing to the system tray, autostart, a global shortcut, saved window position and
  size, a single running instance, and middle-button autoscroll.

#### Built-in modules

- **Habits**: daily tracking, streaks, and statistics.
- **Planner**: daily tasks and weekly goals.
- **Books**: reading journal with Google Books search.
- **Watching**: anime, movies, and series with Shikimori and Kinopoisk integrations.
- **Finance**: income, expenses, and savings accounts.
- **Recipes**: a cookbook with categories, favorites, and photos.
- **Languages**: a personal dictionary, flashcards, and lessons.
- **Study**: subjects, assignments, and deadlines.
- **Projects**: a board for ideas and projects with checklists.
- **Workouts**: programs, multi-week cycles with progression and deload weeks, an exercise catalog,
  a workout journal, timers, 1RM estimation, and barbell plate calculation.
- **Nutrition**: food diary with Open Food Facts search, calorie and macronutrient targets, body
  weight, and water tracking.

#### Synchronization and security

- A self-hosted sync server (Ktor, PostgreSQL, Exposed) with token-based access and a Docker
  Compose deployment.
- Field-level merging with hybrid logical clocks, so edits from different devices combine instead
  of overwriting each other.
- Optional end-to-end encryption of synchronized records.
- Secrets kept in the platform's protected storage.

#### Updates and extensibility

- An update server that publishes the latest version and installers, with update checks in the
  apps.
- External plugins built with the plugin SDK, described by `berill-plugin.json`, and run in separate
  processes; a sample `counters` plugin.
- Release, deployment, and server workflows for GitHub Actions.

[Unreleased]: https://github.com/Akkraizen/Berill/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Akkraizen/Berill/releases/tag/v1.0.0
