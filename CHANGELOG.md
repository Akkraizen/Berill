# Changelog

All notable changes to Berill are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Versions refer to the client
applications (`versionName` in `version.properties`); the sync server, update server, and MCP server
are released together with them.

## [Unreleased]

## [1.3.1] - 2026-11-04

### Added

#### Today

- A Today card at the top of Home gathers what is due across sections: overdue and due project tasks
  and milestones, planner tasks and calendar events, the next workout of the current cycle,
  medication doses, habits, and study deadlines for the next three days. Tasks, doses, habits and
  milestones are checked off right on the card, with progress for the day; done items fold away.
  The card can be hidden or moved in the Home editor like any other card.

#### Gestures

- Swipe left or right over the content to switch between Records and Statistics; past the last tab
  the swipe moves on to the next page of the section (for example from Wellbeing to Sleep), and back.
  Pages slide in the direction of the swipe.
- Swipe rows to act on them: on the Today card right marks an item done and left opens it; project,
  planner and study tasks are completed or submitted with a right swipe and deleted with a left one;
  notes are pinned with a right swipe and deleted with a left one. Deletions can be undone from the
  snackbar.
- Swipe over the month switcher, the calendar month and day, the planner week, day strip and day card,
  the nutrition day and the wellbeing entry to step through months, weeks and days.
- On touch screens these are finger swipes; on desktop a horizontal two-finger trackpad scroll does
  the same for tabs, pages and dates, and rows can be dragged with the mouse.

#### Projects

- Tasks are separate records with a status (to do, in progress, done), priority, due date, milestone,
  details and a linked note, shown as rows, cards or a task board inside the project. Checklists from
  the project text are converted into tasks once, on every device the same way.
- Milestones with due dates and progress counted from their tasks, drawn as a timeline with progress
  rings, a horizontal roadmap or cards; the board card shows task progress and the next milestone.
- Tags, a project deadline, search across projects and their tasks, tag filters, sorting by name,
  deadline, progress, recency or a manual order, and an archive that keeps finished projects off the
  board.
- Notes can be linked to a project or created from it. Task, milestone and project deadlines appear
  in the planner calendar, and tasks due or completed on a day appear in the daily overview and
  global search. Statistics show tasks completed by month.
- Projects settings: the view of the project list and of tasks, and the milestone style.

#### Notes

- Notes can be pinned and given one of eight colours, from the editor toolbar or the card's
  long-press menu; pinned notes are shown first, coloured notes get a tinted card with a stripe.
- `#tags` in the text become filter chips above the list; headings, links and code are not tags.
- Sorting by date modified, title, colour or a manual order arranged by dragging.
- Templates: lecture notes, journal, week plan and meeting, plus any note tagged `#template`
  (`#шаблон` in Russian); `{date}` is replaced with today's date.
- `[[Title]]` links open a record of any section with that exact title (a book, project, recipe,
  workout program and so on) when no note has it.

#### Health

- Patterns on the wellbeing statistics tab compare wellbeing, mood and energy on days with sleep or
  steps above and below your goals over the last 90 days, with the strength of the link.

#### Lists and views

- A list view setting in Appearance (rows or a grid of cards for every section) and a view of its own
  for each list, chosen in the section settings or with the view button above the list: projects as
  a board, cards or rows; project tasks as rows, cards or a board; books and titles as a board, a
  cover grid or rows; recipes as cards or rows; study assignments and the language dictionary as rows
  or cards.
- Select several books, titles, projects or notes from the view button and change their status,
  archive, pin, colour or delete them at once.
- Manual order by dragging for books, titles and projects in cards and rows; the order syncs between
  devices. The manual order of notes is kept on the device.
- Compact density in Appearance tightens sections, lists and cards.

#### AI assistants (MCP)

- `today` and `weekly_review` tools summarize the day and the week across sections: what is due and
  overdue, what was logged each day, notes edited and deadlines of the following week.
- Module actions, exposed as `berill_<module>_<action>` tools, run domain operations with the app's
  own rules:
  - Workouts: assign a saved program to cycle weeks, reset weeks, override one exercise in a week,
    save a 1RM and return the computed plan of a week with resolved weights.
  - Projects: an overview, adding tasks, changing task status, moving or archiving projects.
  - Health: listing and marking medication doses.
  - Study: upcoming deadlines, adding a deadline to a subject, changing an assignment's status.
  - Habits: progress with streaks and marking a habit for a day.
- Read-only actions stay available in read-only mode.

#### For plugin developers

- `TodaySource` adds items to the Today card; sections that only report `DeadlineSource` deadlines
  appear there automatically.
- `PluginContext.notes` reads, links and creates notes.
- `McpSource.actions` with `McpAction` and the `mcpInput` builder describe module actions.
- `ListLayoutKey`, `ListLayoutSwitcher`, `GroupedCollection`, `CardGrid` and `RowList` give a list the
  shared views; `CollectionModes`, `CollectionModeBar`, `ManualOrder` and `Modifier.reorderable` add
  selection, bulk actions and drag ordering; `LocalCompactLayout` reports the compact density.
- `WidgetProvider.watchesAllSections` marks widgets that read other sections, so they refresh on any
  change.
- `Modifier.swipeArea` handles horizontal swipes and trackpad scrolling, with the innermost area that
  accepts a direction taking the gesture; `SwipeActions` adds swipe actions to a row.

### Changed

- Android widgets refresh only when the data they show changes: an edit in one section rebuilds that
  section's widgets (and the summary widgets), unchanged widgets are not redrawn, a widget's own timer
  refreshes just that widget, ring and chart images are cached, and date-based widgets roll over
  right after midnight.
- Workouts: the Hammer Strength chest press («Жим в хаммере») is in the exercise list.
- Notes are shown as a grid of cards of equal height; the global rows setting keeps them as a list.
- Cover cards in Reading and Watching are smaller, so more fit in a row.
- Line charts connect points across days without records with a dashed line, so wellbeing, mood and
  energy read as continuous trends.
- Health and Projects settings are grouped into section cards like the other sections.
- Titles from Watching and workout programs show up in global search.
- Deleted items are erased for good 30 days after deletion; the trash shows how many days each one
  has left.

### Fixed

- Dragging a quick action in the Home editor no longer freezes it halfway once it passes another
  action: the chip keeps following the pointer and settles in its new place.
- Home cards stay in their columns without gaps, each one right under the previous card of its column.
  Dragging a card works like moving app icons on Android: it is inserted into the column under the pointer
  between the cards it is dropped between, only that column makes room, and the cards of the other columns
  keep their places. Columns are remembered separately for each window width.
- Manual ordering in lists takes the place of the card a dragged item is dropped on.
- Saving a note from the editor no longer resets fields it does not edit, such as the pin and colour
  set on another device.

## [1.3.0] - 2026-11-02

### Added

#### Health

- A Health module: medications and supplements with schedules, courses, intake tracking and reminders;
  pulse, blood pressure, temperature, oxygen saturation and blood glucose with charts; daily wellbeing,
  mood, energy and symptoms; manual sleep log together with Health Connect steps and sleep. All of it is
  available to assistants through MCP.
- Manual step entry for any day; manually entered steps are kept when Health Connect imports the same
  day, while imported sleep still updates.
- Health settings: three styles for marking doses (cards, timeline, chips), medication reminders with a
  configurable lead time, daily sleep and step goals, and whether the Home card shows today's activity.
- Quick actions **Wellbeing** and **Log a measurement** open dialogs over Home instead of leaving the page:
  ratings, symptoms and a note for any day, or a measurement with date, time and note, including
  save-and-add-another and undo.

#### Workspace and interface

- Home layout is edited in place: cards and quick actions are dragged directly on the page, a button on
  each card hides it, and hidden items can be restored from a bar above the grid.
- On wide screens, cards in Books and Watching can be dragged between the status columns of the board.
- Bulky elements such as workout program days and cycle weeks collapse to their title and remember
  whether they are expanded.
- An in-app update center with progress, cancellation, persistent verified downloads, release notes,
  Android package/signature checks and system installation, and Windows install/relaunch with saved drafts.
- Signed in-app updates: CI signs every package (ECDSA P-256); builds with `berill.updatePublicKey`
  refuse unsigned or wrongly signed packages before downloading and before installing.
- Full-text search over all records (SQLite FTS4): words are matched by prefix in any language,
  results with the query in the title come first, and the index is built once for existing data.
- Automatic local export on a schedule (daily or weekly) into a chosen folder — a directory on desktop,
  a document tree with persistent access on Android — keeping the latest seven archives.
- A size limit for the attachment cache (256 MB – 2 GB): once the server has every file, the least
  recently used ones are removed from the device and downloaded again when opened.
- A synced template for daily notes with `{{date}}`, `{{weekday}}` and `{{iso}}`, and mood selection in Day.
- `POST /api/blobs/missing` on the sync server, so clients re-upload only the attachments it lacks.

#### Workouts

- A Material 3 Expressive workout radar comparing completed sets or tonnage by muscle region with the
  preceding equal-length period, including custom catalog exercises and accessible group totals.
- The radar can compare actual calendar-week load with a selected program treated as a weekly plan,
  with week navigation and explicit warnings for incomplete planned tonnage.

#### AI assistants (MCP)

- Ready-made MCP connection settings for Claude Desktop, Claude Code, OpenAI Codex, Cursor, VS Code,
  Windsurf, Gemini CLI, Zed, opencode, Cline / Roo Code and JetBrains AI Assistant, both in the desktop
  settings and through `berill-mcp config --client`.

### Changed

- Sections that have both records and statistics — Books, Watching, Projects, Study, Planner, Finance,
  Languages, Habits, Health and the workout journal — split them into the same localized tabs, keeping
  each tab's scroll and filters.
- Page headers are lighter: duplicated page titles are gone, page actions sit next to the tabs, and the
  title bar shows the date subtitle only on Home.
- Book and title cards open their details from the title only, so the rest of the card can be dragged.
- Home settings show the editable Home page itself instead of a settings panel, a preview and a list of cards.
- Sleep durations are shown as hours and minutes (for example, “8 h 15 min”) instead of minutes.
- MCP lists return a short `etag` per record instead of repeating all fields as `expected`; updates and
  deletes accept the etag (older clients may still pass `expected`).
- Stored enum values of older versions live in each plugin's `data/LegacyValues.kt` instead of translation
  files; typed saves keep a stored value this version does not understand unless that field was edited.
- Base snapshots for conflict detection are kept only while a record has unsent changes; old ones are
  removed once. The conflict journal is no longer re-read after every synchronization.
- Global search, archive export/import and status-code handling were moved out of UI code into
  `LocalArchiveService` and shared helpers; deleted records for the trash are queried in SQL.

### Fixed

- The MCP STDIO bridge forwards requests concurrently, so long tool calls no longer block pings,
  cancellations and parallel calls; concurrent requests share one session recovery, and malformed
  input gets a JSON-RPC parse error instead of being ignored.
- `berill-mcp` no longer fails with `SQLITE_BUSY` under parallel tool calls: its database uses a single
  shared connection in WAL mode with a busy timeout.
- Health Connect steps and sleep are shown once in daily summaries when the Health module is enabled.
- The “Saved” message with undo from one quick-entry dialog no longer reappears in the next dialog and
  covers its buttons.
- Cached recipe photos load without waiting for network uploads. Attachment reads run off the UI thread,
  use a bounded 24 MiB memory cache backed by persistent local files, retry temporary failures, and
  expose image decoding errors.
- Workout journal views (entries and statistics) switch with a Material 3 Expressive connected button
  group instead of tabs that left a grey rectangle behind the selected tab (`BerillTabs` in the UI kit).
- A missing or rejected (HTTP 413) attachment no longer blocks all synchronization; it is skipped and logged.
- Reminders are rescheduled once after a burst of changes instead of on every autosaved keystroke.
- Stamps from a device whose clock runs far ahead no longer poison other devices: the server rejects them
  with `clock_skew` (the app asks to fix the date and time), local clocks ignore them, unsent changes are
  re-stamped after the clock is corrected, and edits of such existing data still win.
- The update center test expects the install button label of the platform it runs on, so macOS and
  Linux release builds pass.

## [1.2.0] - 2026-11-01

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
- The built-in MCP server settings offer configuration for compatible MCP clients in general.

### Fixed

- Windows installer build, publication and update discovery consistently use `-windows.exe`; legacy
  `-Setup.exe` downloads remain supported.
- Sync deployment transfers the backup Dockerfile and builds its local image before recreating containers.
- Release widget tests respect the selected language on English CI runners; failed Windows test reports
  are retained as workflow artifacts.
- Cross-module synchronization recovery, logical clocks and reconciliation after server/dataset changes.
- Unsaved note drafts are protected against concurrent synchronization and MCP edits, with conflicts retained.
- Rescheduled planner tasks remain planned and incomplete on the source day and planned on the destination day.
- API failures are reported consistently without silently dropping failed operations.
- Windows Hot Reload uses separate startup libraries per launch to avoid deleting JARs locked by a previous JVM.
- Header action hover/press feedback, icon sizes and colors, stale highlighting after popup dismissal,
  dropdown widths and unwanted progress-bar stop indicators.
- Remote backup failures preserve local backups; restic retention groups snapshots consistently even though
  dump filenames change, and does not mistake connection/authentication errors for an uninitialized repository.

## [1.1.0] - 2026-10-28

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

[Unreleased]: https://github.com/Akkraizen/Berill/compare/v1.3.1...HEAD
[1.3.1]: https://github.com/Akkraizen/Berill/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/Akkraizen/Berill/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/Akkraizen/Berill/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/Akkraizen/Berill/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Akkraizen/Berill/releases/tag/v1.0.0
