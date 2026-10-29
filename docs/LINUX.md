# Linux

How the Linux version of Berill is built, tested, and distributed. Server deployment is covered in
[DEPLOY.md](DEPLOY.md), the release process in [RELEASES.md](RELEASES.md).

## Contents

- [Overview](#overview)
- [Building](#building)
- [Testing in CI](#testing-in-ci)
- [Distribution](#distribution)
- [Related documents](#related-documents)

## Overview

The Linux client is the JVM desktop app (`app/desktopApp`), packaged by Compose Desktop into `.deb`
and `.rpm` packages with a bundled Java runtime.

| Path                          | Purpose                                                |
|-------------------------------|--------------------------------------------------------|
| `scripts/linux/Berill.png`    | App icon.                                              |
| `.github/workflows/linux.yml` | CI: desktop tests and Linux packages (`.deb`, `.rpm`). |

## Building

On a Linux machine:

```sh
sudo apt-get install -y fakeroot rpm
./gradlew :app:desktopApp:run
./gradlew :app:desktopApp:packageReleaseDeb :app:desktopApp:packageReleaseRpm
```

The first command runs the app from source; the packaging tasks create `.deb` and `.rpm` packages in
`app/desktopApp/build/compose/binaries/main-release/`.

## Testing in CI

The **Linux** workflow (`.github/workflows/linux.yml`) runs on GitHub's Ubuntu runners on every push
that touches app code, on pull requests, and manually. It runs the desktop tests and builds `.deb`
and `.rpm` packages, which are available as artifacts of the run.

## Distribution

The **Release** workflow builds the Linux `.deb` and `.rpm` packages on an Ubuntu runner next to the
Windows, macOS, and Android files, and **Publish update** uploads them to the update server.

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [DEPLOY.md](DEPLOY.md) — server setup, deployment, and maintenance.
- [RELEASES.md](RELEASES.md) — versioning, signing, and building releases.
- [EXTERNAL_PLUGINS.md](EXTERNAL_PLUGINS.md) — writing third-party plugins.
