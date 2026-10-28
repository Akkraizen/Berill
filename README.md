# Berill

Berill is a local-first personal workspace for **Android, Windows, and macOS**. It brings notes,
planning, habits, personal collections, workouts, and nutrition into one application, with optional
synchronization through a self-hosted server.

The clients share Kotlin Multiplatform business logic and a Compose Multiplatform interface.
Everyday editing uses a local SQLite database; a server is only needed to synchronize devices.
Online catalog searches and remote images require an internet connection.

## Contents

- [Features](#features)
- [Platforms and desktop behavior](#platforms-and-desktop-behavior)
- [Project structure](#project-structure)
- [Development setup](#development-setup)
- [Running the application](#running-the-application)
- [Self-hosted synchronization](#self-hosted-synchronization)
- [Data storage and migration](#data-storage-and-migration)
- [Plugin development](#plugin-development)
- [Tests and dependency maintenance](#tests-and-dependency-maintenance)
- [Troubleshooting](#troubleshooting)
- [Further documentation](#further-documentation)

## Features

### Notes and everyday organization

- Markdown notes and editing.
- A home dashboard with cards and quick actions supplied by enabled modules.
- Android home screen widgets for habits, plans, workouts, nutrition, reading, finances, notes, and
  more, with a shared widget model ready for other platforms.
- Daily tasks, weekly goals, habits, streaks, and statistics.
- Projects and ideas organized on a board, with checklists.
- Subjects, assignments, and deadlines for study.
- Russian and English localization.
- Light, dark, and system appearance modes with configurable accent colors.
- Onboarding for language, appearance, and module selection.

### Built-in modules

| Module    | Purpose                                                                                                   |
|-----------|-----------------------------------------------------------------------------------------------------------|
| Habits    | Daily tracking, streaks, and statistics.                                                                  |
| Planner   | Daily tasks, weekly goals, and a calendar with two-way Google Calendar sync.                              |
| Books     | Reading journal and Google Books search.                                                                  |
| Watching  | Anime, movies, and series, with Shikimori and Kinopoisk integrations.                                     |
| Finance   | Income, expenses, and savings accounts.                                                                   |
| Recipes   | A cookbook with categories and favorites.                                                                 |
| Languages | A personal dictionary, flashcards, and lessons.                                                           |
| Study     | Subjects, assignments, and deadlines.                                                                     |
| Projects  | A board for ideas and projects, with checklists.                                                          |
| Workouts  | Training programs, multi-week cycles, exercise catalog, workout journal, timers, and weight calculations. |
| Nutrition | Food diary, Open Food Facts search, calorie and macronutrient targets, body weight, and water tracking.   |

Some external integrations require their own configuration or API credentials. The core local
workflow does not require these integrations.

### Workouts

- Create reusable programs with training days and exercises.
- Assign programs to selected weeks of a multi-week cycle.
- Automatically apply source-program edits to linked weeks while preserving applicable exercise
  overrides.
- Configure progression, deload weeks, and individual week adjustments.
- Enter working weights in kilograms or as a percentage of a saved one-repetition maximum (1RM).
- Resolve percentages into working weights using the corresponding exercise maximum and configured
  rounding.
- Track repetitions, continuous durations, or timed sets with rest intervals.
- Mark individual sets, an entire exercise, or all sets in a workout as complete.
- Estimate 1RM, save exercise maximums, and calculate barbell plate loading.
- Browse strength, calisthenics, and cardio exercises, or add custom exercises.

When a percentage-based exercise has no saved 1RM, the interface indicates that the maximum is
missing. Editing a program does not rewrite workouts already recorded in the journal.

## Platforms and desktop behavior

The Android application requires **Android 8.0 / API 26 or newer**. The desktop client runs on
Windows and **macOS 12 or newer**; see [docs/MACOS.md](docs/MACOS.md) for what differs on macOS.

On Windows and macOS:

- Closing the main window hides it in the system tray (the menu bar on macOS) when tray support is
  available. On macOS, clicking the Dock icon brings the window back and ⌘Q quits.
- The tray menu restores the application or exits it completely.
- Window position, normal size, and maximized state are restored between launches.
- Saved bounds are adjusted when a previously used monitor is unavailable.
- Holding the middle mouse button and moving the pointer vertically enables accelerated autoscroll
  in supported lists and panels. Let go of the button to stop.
- The autoscroll cursor indicates the neutral position and scrolling direction.

## Project structure

| Path / Gradle module        | Responsibility                                                                                        |
|-----------------------------|-------------------------------------------------------------------------------------------------------|
| `protocol`                  | Shared synchronization records, hybrid logical clocks, and field-level merge rules.                   |
| `core-api`                  | Built-in plugin contracts, data access, navigation, capabilities, and reusable Compose UI components. |
| `core`                      | Local database, synchronization client, secure storage, application shell, notes, and plugin hosting. |
| `plugin-sdk`                | SDK and communication contracts for external plugins.                                                 |
| `plugins/*`                 | Built-in feature modules.                                                                             |
| `external-plugins/counters` | Example external plugin.                                                                              |
| `app/shared`                | Composition root registering the built-in plugins.                                                    |
| `app/androidApp`            | Android entry point and resources.                                                                    |
| `app/desktopApp`            | Desktop entry point, window management, tray, and Windows and macOS integration.                      |
| `server`                    | Ktor synchronization server backed by PostgreSQL and Exposed.                                         |
| `mcp`                       | MCP tools, the server embedded in the desktop app, and the `--mcp` stdio bridge.                      |
| `mcp-server`                | Standalone MCP server that gives AI assistants access to Berill data as a separate sync device.       |
| `update-server`             | Ktor update server that serves the latest app version and its installers.                             |
| `deploy`                    | Docker Compose stack, `.env` template, and backup script for production.                              |
| `scripts`                   | Deployment, release, Windows installer, and dependency-lock scripts.                                  |
| `docs`                      | Deployment, release, and external plugin guides.                                                      |
| `build-logic`               | Gradle conventions for built-in and external plugins.                                                 |

Built-in modules use the host API rather than accessing the application's database or shell
directly. External plugins use a separate SDK and are hosted in separate processes.

## Development setup

Use these tools for the configured build:

| Tool                      | Requirement                                                               |
|---------------------------|---------------------------------------------------------------------------|
| JDK                       | Java 21.                                                                  |
| Gradle                    | Use the checked-in wrapper; no separate Gradle installation is needed.    |
| Android SDK               | Platform 37.0 and Build Tools 37.0.0 for Android builds.                  |
| Android device / emulator | API 26 or newer.                                                          |
| macOS builds              | A Mac for packaging the DMG; see `docs/MACOS.md`.                         |
| Sync-server deployment    | PostgreSQL; Docker Compose is supported by the supplied deployment files. |

Dependency versions are maintained in `gradle/libs.versions.toml`; the Gradle version is maintained
in `gradle/wrapper/gradle-wrapper.properties`.

Clone the repository into a directory of your choice:

```powershell
git clone https://github.com/Akkraizen/Berill.git
cd Berill
```

Open the root project in Android Studio or a compatible IntelliJ IDEA installation. Select JDK 21
for Gradle and let the project synchronize.

For Android builds, configure the SDK through the IDE or create an untracked `local.properties` in
the repository root:

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

The commands below use Windows PowerShell syntax. On Linux or macOS, use `./gradlew` instead of
`.\gradlew.bat` for compatible Gradle tasks.

## Running the application

Run the desktop application from source:

```powershell
.\gradlew.bat :app:desktopApp:run
```

Build an Android debug APK:

```powershell
.\gradlew.bat :app:androidApp:assembleDebug
```

Install it on a connected device or running emulator:

```powershell
.\gradlew.bat :app:androidApp:installDebug
```

## Self-hosted synchronization

### How synchronization works

Clients read and write their local SQLite database. When synchronization is configured, they
exchange changes with the Ktor server over the authenticated `/api/sync` endpoint.

The shared protocol uses field-level hybrid logical clock timestamps to merge records. Server
revisions provide an incremental synchronization cursor. Devices using tokens for the same server
user share that user's synchronized data.

The application supports optional end-to-end encryption for synchronized record content. Other
devices need the corresponding encryption password to unlock that content. This does not imply that
the local SQLite database is encrypted, and HTTPS is still needed for transport security.

### Run the server locally

Provide a PostgreSQL database and set its connection details:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/berill'
$env:DATABASE_USER = 'berill'
$env:DATABASE_PASSWORD = Read-Host 'Database password'
.\gradlew.bat :server:run
```

The default HTTP port is `8080`; override it with `PORT` for a standalone server. `GET /health`
returns `ok` when the application is running. Synchronization requests require an access token.

### Deploy with Docker Compose

The synchronization server and the update server are separate services with separate images.
Deployment files live in `deploy/`:

```powershell
Copy-Item deploy/.env.example deploy/.env
cd deploy
```

Edit `.env` and replace `DATABASE_PASSWORD` with a strong database password. The Compose file also
accepts `DATABASE_NAME`, `DATABASE_USER`, and `DATABASE_VOLUME`; the defaults are `berill`,
`berill`, and `berill-db`.

Start the stack:

```powershell
docker compose up -d --build
```

This starts `db`, `sync` (port `8080`), `updates` (port `8081`, serves files from `releases/`), and
`backup`. Each Dockerfile builds its server from source with the pinned Gradle wrapper; no local JDK,
Gradle, or Android SDK is required. The build context is the repository root, for example
`docker build -f server/Dockerfile -t berill-sync .` or
`docker build -f update-server/Dockerfile -t berill-updates .`. PostgreSQL data is stored in a named
volume. See [docs/DEPLOY.md](docs/DEPLOY.md) for the CI-based production deployment.

For an internet-facing deployment, configure an HTTPS reverse proxy such as Caddy or nginx. Enter
its public server address in the application's synchronization settings.

### Create and manage access tokens

From the `deploy` directory with the Compose stack running:

```powershell
docker compose exec sync java -jar /app/berill-server.jar token create akkraizen --name desktop
docker compose exec sync java -jar /app/berill-server.jar token list akkraizen
docker compose exec sync java -jar /app/berill-server.jar token revoke TOKEN_ID
```

The create command displays the new token once. Copy it into **Settings → Sync → Access token** in
the application and save the server settings. Create additional device tokens for the same user when
needed. The database-backed token store keeps token hashes rather than recoverable token values.

For environment-based configuration, `HUB_TOKENS` also accepts comma-separated `user:token` entries.
Tokens in this configuration must be at least 16 characters long. An empty `HUB_TOKENS` value is
valid when database-backed tokens are used; if neither token source is configured, synchronization
is rejected.

## Data storage and migration

On Windows, application data is stored under `%USERPROFILE%\.berill` (`~/.berill` on macOS):

- `berill.db`: the local SQLite database.
- `window.properties`: saved window geometry and maximized state.
- `secrets/`: desktop secret-storage files. On macOS, secrets are kept in the login keychain.
- `plugins/`: installed external plugin files.

Back up local data while Berill is fully closed through the tray menu. Back up the server database
volume separately when operating a sync server. Synchronization replicates changes, including
deletions; maintain independent backups for recovery.

The Android application ID and the macOS bundle identifier are `dev.akkraizen.berill`. An
installation using a different application ID is a separate application; transfer its data through
configured synchronization where applicable.

The desktop migration code can copy data from the previous installation name using a consistent
SQLite snapshot. It preserves the original files and does not overwrite an existing Berill database.
Compatibility handling also exists for previous desktop secrets and synchronization-encryption
profiles.

When updating an existing Docker deployment, retain its actual database name, database user,
password, and volume name in `.env`. Renaming the source directory or repository does not migrate an
existing database volume.

## Plugin development

### Built-in plugins

Built-in modules use the `berill.plugin` Gradle convention and the `core-api` contracts. Register a
new module in `settings.gradle.kts` and add its plugin instance to the composition root in
`app/shared`.

Existing modules provide examples of pages, settings, home cards, quick actions, localized text, and
collection-backed data.

### External plugins

External plugins use `plugin-sdk` and the `berill.external-plugin` convention. A package includes a
`berill-plugin.json` manifest describing its entry point, pages, collections, and requested
permissions.

Build the example:

```powershell
.\gradlew.bat :external-plugins:counters:packageBerillPlugin
```

The default output is `external-plugins/counters/build/berill/counters.tplugin`. The convention uses
Android SDK tooling to include Android code; without the required SDK tooling, the package is
Windows-only.

Install a package through the application's external-plugin settings, review its requested
permissions, and restart the application when prompted. External plugins run in separate processes
and interact with host-provided data, UI, navigation, and network capabilities.

The manifest format, UI builder, and sandbox rules are described in
[docs/EXTERNAL_PLUGINS.md](docs/EXTERNAL_PLUGINS.md).

## Tests and dependency maintenance

Run the JVM and server tests:

```powershell
.\gradlew.bat jvmTest :server:test --no-configuration-cache
```

Run a focused subset during development:

```powershell
.\gradlew.bat :protocol:jvmTest :core:jvmTest :plugins:workouts:jvmTest --no-configuration-cache
```

Compile both clients:

```powershell
.\gradlew.bat :app:desktopApp:compileKotlin :app:androidApp:compileDebugKotlin --no-configuration-cache
```

Dependency locking is enabled by the root build. Use the scripts in
`scripts/update-dependency-locks.ps1` or `scripts/update-dependency-locks.sh` when intentionally
refreshing dependency locks, and review the resulting changes before committing them.

## Troubleshooting

| Symptom                                              | What to check                                                                 |
|------------------------------------------------------|-------------------------------------------------------------------------------|
| Android SDK is not found                             | Configure the SDK in the IDE or set `sdk.dir` in `local.properties`.          |
| Closing the desktop window does not stop the process | Use **Exit** in the tray menu to terminate Berill.                            |
| Synchronization is unauthorized                      | Check the token, its revocation status, and the server configuration.         |
| Encrypted synchronization remains locked             | Unlock the encryption profile with the corresponding password on that device. |
| A Docker image build cannot find the server JAR      | Build from the repository root with `docker build -f server/Dockerfile .` (or `update-server/Dockerfile`).                          |

The build files define the current tool versions and platform configuration.

## Further documentation

| Document | Contents |
| --- | --- |
| [docs/DEPLOY.md](docs/DEPLOY.md) | Production stack, server and GitHub setup, deployment, backups, maintenance. |
| [docs/RELEASES.md](docs/RELEASES.md) | Versioning, Android signing, release workflow, publishing updates. |
| [docs/EXTERNAL_PLUGINS.md](docs/EXTERNAL_PLUGINS.md) | Writing, packaging, and installing third-party plugins. |
| [docs/MACOS.md](docs/MACOS.md) | Building, testing, and distributing the macOS app. |
| [docs/WIDGETS.md](docs/WIDGETS.md) | Home screen widgets: the shared model, the Android host, and adding other platforms. |
| [docs/MCP.md](docs/MCP.md) | Connecting Claude and other AI assistants through the MCP server. |
| [docs/GOOGLE_CALENDAR.md](docs/GOOGLE_CALENDAR.md) | Setting up Google Cloud and syncing the planner calendar with Google Calendar. |
| [scripts/windows-installer/README.md](scripts/windows-installer/README.md) | Building the Windows installer locally. |

Website: [berill.space](https://berill.space). Downloads: [latest release](https://github.com/Akkraizen/Berill/releases/latest).
