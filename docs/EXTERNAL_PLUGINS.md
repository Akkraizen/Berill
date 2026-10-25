# External plugins

An external plugin is a Berill section written by someone other than the Berill author. It runs **in
a separate process** and sees only what the app gives it: its own data, its own settings, and HTTPS
access to the hosts listed in its manifest. Files, other sections, network access outside the
manifest, threads, and reflection are unavailable (Windows: bytecode verification and a class filter;
Android: an isolated process without permissions).

## Contents

- [Project setup](#project-setup)
- [Manifest](#manifest)
- [Code](#code)
- [Build and install](#build-and-install)
- [Forbidden APIs](#forbidden-apis)
- [Related documents](#related-documents)

## Project setup

Inside the Berill repository, add a module and apply the convention plugin:

```kotlin
// settings.gradle.kts
include(":my-plugin")

// my-plugin/build.gradle.kts
plugins { id("berill.external-plugin") }
```

Outside the repository, publish the SDK locally with `./gradlew :plugin-sdk:publishToMavenLocal`,
then add `implementation("dev.akkraizen.berill:plugin-sdk:0.1.0")` to your Kotlin Multiplatform
project and reuse the same packaging steps (see `BerillExternalPluginConvention`). A complete example
is in `external-plugins/counters`.

## Manifest

`berill-plugin.json` lives in the module root:

```json
{
  "id": "counters",
  "version": "1.0.0",
  "name": { "ru": "Счётчики", "en": "Counters" },
  "description": { "en": "Tally counters for anything" },
  "author": "Your name",
  "entry": "com.example.berill.counters.CountersPlugin",
  "icon": "timer",
  "pages": [{ "id": "counters", "title": { "ru": "Счётчики", "en": "Counters" }, "icon": "timer" }],
  "homeCards": [{ "id": "total", "title": { "ru": "Счётчики", "en": "Counters" } }],
  "hasSettings": true,
  "collections": ["counters_items"],
  "permissions": { "network": ["api.example.com"] }
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `id` | yes | 2–32 characters: `a-z`, `0-9`, `_`, starting with a letter. |
| `version` | yes | 1–32 characters. |
| `name` | yes | Localized text, 1–60 characters per language. |
| `description`, `author` | no | Shown in the plugin settings. |
| `entry` | yes | Fully qualified class name. Packages such as `java.`, `kotlin.`, `android.`, and `dev.akkraizen.berill.` are reserved. |
| `icon` | no | Icon name; defaults to `puzzle`. |
| `pages` | yes | At least one page. Page IDs use `a-z`, `0-9`, `_`, `-` and must be unique. |
| `homeCards` | no | Cards on the home dashboard; IDs must be unique. |
| `hasSettings` | no | Whether the plugin provides a settings page. |
| `collections` | no | Data collections. Each must start with `<id>_`, up to 40 characters. |
| `permissions.network` | no | Hosts for `host.http.getJson` and images, such as `api.example.com` or `*.example.com` for subdomains. HTTPS only. |

## Code

```kotlin
class CountersPlugin : ExternalPlugin() {
    private val items by lazy { host.data.collection<Counter>("counters_items") }
    private var draft = ""

    override fun UiBuilder.page(pageId: String, route: String?) {
        card(tr("Новый", "New"), icon = "add") {
            row {
                weight { textField(draft, tr("Название", "Title"), onSubmit = { add() }) { draft = it } }
                button(tr("Добавить", "Add"), enabled = draft.isNotBlank()) { add() }
            }
        }
        items.all().forEach { (id, c) -> listItem(c.title, trailing = "${c.value}") { host.nav.open(id) } }
    }
}
```

- After every handler (button, input) the app requests the UI again, so keep state in plugin fields
  or in `host.data`. Call `host.invalidate()` to redraw outside a handler.
- `route` is an inner screen of a page (`host.nav.open(route)`); `host.nav.back()` goes back.
- UI nodes: `text`, `header`, `card`, `row` / `column` / `weight`, `button`, `iconButton`,
  `textField`, `checkbox`, `switch`, `chips`, `stats`, `progress`, `listItem`, `divider`, `spacer`,
  `empty`, `image`, `markdown`.
- Icons by name: `add`, `delete`, `edit`, `check`, `close`, `star`, `back`, `search`, `settings`,
  `info`, and the app catalog (`task`, `fire`, `book`, `fitness`, `food`, `water`, `idea`, `timer`,
  …).
- `Host` errors are thrown as `HostException(code)`: `denied`, `http_<code>`, `network`, `timeout`.

## Build and install

```sh
./gradlew :my-plugin:packageBerillPlugin
```

The package is written to `my-plugin/build/berill/my-plugin.tplugin`. Android support requires the
Android SDK (`d8`): set `sdk.dir` in `local.properties`. Without it the package is Windows-only.

To install, open the external plugins section in the app settings, choose to install from a file,
review the requested permissions, and restart the app when prompted.

## Forbidden APIs

A plugin that uses any of the following is rejected before it is loaded:

- `java.io`, `java.net`, `java.nio.file`;
- `Thread`, `java.util.concurrent`, `kotlin.concurrent`;
- `System`, `Runtime`, `ProcessBuilder`, `kotlin.system`;
- `ClassLoader`, reflection (`Class.forName`, `getDeclaredMethod`, …), `MethodHandle`;
- `kotlin.io` (except `println`);
- app classes.

Allowed: the Kotlin standard library, kotlinx.serialization, Java collections, dates, and numbers,
and the SDK.

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [DEPLOY.md](DEPLOY.md) — server setup, deployment, and maintenance.
- [RELEASES.md](RELEASES.md) — versioning, signing, and building releases.
