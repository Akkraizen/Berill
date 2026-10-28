# Changelog

All notable changes to Berill are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Versions refer to the client
applications (`versionName` in `version.properties`); the sync server, update server, and MCP server
are released together with them.

## [Unreleased]

### Added

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
  read-only and read-write modes, token management, and ready-to-copy configuration for Claude
  Desktop and Claude Code.
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

- Plugin sources are split into `data`, `ui`, and `i18n` packages.
- MCP tools moved into a shared `mcp` module used by both `berill-mcp` and the desktop app.
- The desktop runtime image now includes the `java.net.http` module.

### Fixed

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
