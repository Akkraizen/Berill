# Home screen widgets

Berill widgets are split into a platform-neutral part shared by every client and a thin host per
platform. Today only the Android host exists; the shared part is designed so that Windows, macOS
and iOS hosts can be added without touching module code.

## Layers

| Layer | Location | Responsibility |
|-------|----------|----------------|
| Contract | `core-api/.../api/widgets/Widgets.kt` | `WidgetProvider`, the serializable content model (`WidgetContent`, `WidgetBlock`, `WidgetAction`, `WidgetEffect`) and widget sizes. |
| Providers | `plugins/*/widgets/*.kt`, `core/.../widgets/CoreWidgets.kt` | Build content from module data and handle widget actions. Common code only. |
| Hub | `core/.../widgets/WidgetHub.kt` | Registers providers from all modules, hides widgets of disabled modules, isolates provider failures, translates `Open` actions into navigation, emits `changes`, and produces JSON snapshots. |
| Android host | `app/androidApp/.../widgets/` | Jetpack Glance receivers, renderer, action callbacks, configuration activity, scheduled refreshes. |

A module adds widgets by overriding `BerillPlugin.widgets(ctx)`. Each provider has an `id`; the hub
exposes it under the key `<pluginId>.<id>` (core widgets use the `core` owner).

## Content model

A provider returns a `WidgetContent`: a title, an optional subtitle and icon, an action for tapping
the header, up to two header buttons, and a list of blocks. Blocks describe intent rather than
layout: `Checks`, `Rows`, `Buttons`, `Bar`, `Metrics`, `Ring`, `Sparkline`, `Grid`, `Month`, `Countdown`,
`Card` and `Text`. Every type is `@Serializable`, so the same content can be cached, sent to
another process, or written for a native widget extension.

Actions:

- `Open(route, page, pluginId)` opens a module screen; `OpenNote` and `OpenHome` open core screens.
- `Run(id, args, opensApp)` is handled by the provider's `perform`. It returns `Refresh(state,
  refreshAtMillis)` to update the widget in place, or `OpenApp` when the action leads into the app
  (for example, starting a workout). Set `opensApp = true` on such actions so hosts can launch the
  app directly from the tap.

Per-instance state (`WidgetRequest.state`) is stored by the host, not the provider. It is used for
things like the revealed flash card or the end time of the rest timer. Providers that need the user
to pick something (a note, a language) declare `choices`; `required = false` makes the choice
optional.

## Built-in widgets

| Key | Widget |
|-----|--------|
| `core.today` | Day summary built from every provider's `summary()` |
| `core.note_quick` | New note button and recent notes |
| `core.note_pinned` | A chosen note; markdown checkboxes can be toggled |
| `core.journal` | Daily mood buttons and a line about the day, stored in a daily note |
| `habits.today` | Today's habits as checkboxes |
| `habits.week` | 7-day grid and per-habit streaks |
| `planner.today` | Today's tasks (toggleable) and calendar events |
| `planner.calendar` | Month grid with event markers, month switching, and the selected day's events |
| `planner.agenda` | Schedule of events for the next two weeks, grouped by day |
| `workouts.next` | Next day of the active cycle or program, or the workout in progress |
| `workouts.rest` | Rest timer with presets |
| `nutrition.calories` | Calories ring and macronutrient bars |
| `nutrition.water` | Water progress and glass buttons |
| `nutrition.weight` | Weight sparkline |
| `books.reading` | Books being read |
| `watching.now` | Titles being watched with a “+1 episode” button |
| `languages.card` | Flash card from the dictionary |
| `study.deadlines` | Nearest deadlines from every `DeadlineSource` |
| `finance.month` | Free money, income, expenses, top categories |
| `finance.quick` | Category buttons that open the expense entry |
| `recipes.day` | Recipe of the day, favorites first |

## Android host

- Every built-in widget has its own receiver so it appears separately in the launcher's widget
  picker. `AnyWidgetReceiver` (“Berill: any widget”) lets the user pick any available widget,
  including ones contributed by modules that have no dedicated receiver yet.
- `WidgetCatalog` maps receiver classes to widget keys. When adding a provider that should have its
  own picker entry, add a receiver class, a catalog entry, an `appwidget-provider` XML file, labels
  in `values/strings.xml` and `values-ru/strings.xml`, and a `<receiver>` element in the manifest.
- Content is computed on `Dispatchers.IO`, stored as JSON in the Glance state, and rendered by
  `WidgetRenderer`. The countdown uses a native `Chronometer`, rings and sparklines are drawn into
  bitmaps.
- `Run` actions go through `WidgetActionCallback`. Actions that open the app use an activity intent;
  `MainActivity` hands it to the hub, which updates navigation before the UI is shown.
- Widgets refresh on every local or synchronized data change while the app process is alive, every
  30 minutes through `updatePeriodMillis`, and at `refreshAtMillis` through WorkManager.

## Adding other platforms

The hub already provides what a native host needs:

- `WidgetHub.available()` and `choices()` for a picker;
- `content(instance)` and `perform(instance, action)` for rendering and actions;
- `changes` to know when to refresh;
- `snapshot()` and `encodeSnapshot()` to export the content of all widgets as one JSON document.

Windows can render `WidgetContent` with Compose Desktop in a small always-on-top window or a tray
popup, reusing the desktop process and its `BerillCore`.

On iOS and macOS, WidgetKit extensions run in a separate process and are written in Swift. The
intended flow is: the app writes `encodeSnapshot()` into a shared App Group container whenever
`changes` emits and asks WidgetKit to reload timelines; the Swift extension decodes the snapshot and
draws each block; taps open the app with a deep link that carries the encoded `WidgetAction`, which
the app passes to `perform`. `Run` actions that do not open the app can use App Intents that call
back into the shared Kotlin framework.
