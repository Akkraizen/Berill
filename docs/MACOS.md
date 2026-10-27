# macOS

How the macOS version of Berill is built, tested, and distributed. Server deployment is covered in
[DEPLOY.md](DEPLOY.md), the release process in [RELEASES.md](RELEASES.md).

## Contents

- [Overview](#overview)
- [Differences from Windows](#differences-from-windows)
- [Building](#building)
- [Testing without a Mac](#testing-without-a-mac)
- [Distribution](#distribution)
- [Related documents](#related-documents)

## Overview

The macOS client is the same JVM desktop app as on Windows (`app/desktopApp`), packaged by Compose
Desktop into a `.dmg` with a bundled Java runtime. It requires macOS 12 or newer.

| Path | Purpose |
| --- | --- |
| `app/desktopApp/.../desktop/macos` | macOS integration: login item, global shortcut, Dock and ⌘Q handling. |
| `core/src/jvmMain/.../secure/MacKeychain.kt` | Desktop secrets in the macOS login keychain. |
| `scripts/macos/Berill.icns` | App icon. |
| `.github/workflows/macos.yml` | CI: desktop tests and the DMG. |

## Differences from Windows

| Feature | macOS |
| --- | --- |
| Local data | `~/.berill`, as on Windows |
| Secrets (sync token, encryption keys) | Login keychain; falls back to `~/.berill/secrets` with `0600` permissions if the keychain is unavailable |
| Window | Native traffic-light buttons, resizing, and full screen; Berill's title bar is drawn under a transparent system title bar |
| Background | Closing the window keeps Berill in the menu bar; clicking the Dock icon brings it back; ⌘Q quits |
| Global shortcut | Off by default; choose `Ctrl+Option+B` or another combination in settings |
| Autostart | Login item (`~/Library/LaunchAgents/dev.akkraizen.berill.plist`) for the installed app |
| Pasting images | ⌘V instead of Ctrl+V |
| Updates | `Berill-<version>-macos.dmg` from the update server |

## Building

On a Mac:

```sh
./gradlew :app:desktopApp:run
./gradlew :app:desktopApp:packageReleaseDmg
```

The first command runs the app from source; the second writes
`app/desktopApp/build/compose/binaries/main-release/dmg/Berill-<version>.dmg`.

## Testing without a Mac

The **macOS** workflow (`.github/workflows/macos.yml`) runs on GitHub's macOS runners on every push
that touches app code, on pull requests, and manually. It runs the desktop tests and builds the DMG,
which is available as the `macos-dmg` artifact of the run.

## Distribution

The **Release** workflow builds the DMG on a macOS runner next to the Windows and Android files,
and **Publish update** uploads it to the update server, which offers it to macOS clients.

The DMG is not signed with a Developer ID certificate or notarized. On first launch, open it from
Finder with **Control-click → Open**, or allow it in **System Settings → Privacy & Security**.
Because the app is unsigned, macOS may ask again for keychain access after an update; choose
**Always Allow**. Signing and notarization need the Apple Developer Program.

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [DEPLOY.md](DEPLOY.md) — server setup, deployment, and maintenance.
- [RELEASES.md](RELEASES.md) — versioning, signing, and building releases.
- [EXTERNAL_PLUGINS.md](EXTERNAL_PLUGINS.md) — writing third-party plugins.
