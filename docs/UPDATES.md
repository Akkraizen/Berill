# In-app update center

Open **Settings → Updates**. The same download and installation controls appear on Home when
a newer version is available. The center shows installed/available versions and release notes,
downloads privately with progress and cancellation, and retains a completed package across restarts.
Discard removes the package. Leaving the settings page does not cancel a download.

## Server rollout

Deploy the updated update-server before distributing this client. `/api/release` keeps its legacy
download fields and adds per-platform `artifacts` containing a download path, SHA-256 and byte size,
plus optional release notes. Existing clients ignore these added fields. The server computes hashes
from the actual release files. Existing releases do not need to be rebuilt. Treat published package
files as immutable. The publishing workflow copies the GitHub release body as `Berill-<version>-notes.md`.

An old server remains usable for checking versions, but in-app installation is disabled until it supplies
verification metadata. Packages are downloaded only over HTTPS; loopback HTTP is allowed for local
development/tests. Redirects are refused. The downloader enforces the declared length, a 1 GiB maximum,
SHA-256, expected release filename/version, and private temporary paths. Failed/cancelled streams remove
their partial file. The completed package is checked again immediately before installation.

## Windows installations

The updater is intended for the MSI/Inno-installed Windows application. It checks the registered
installation location and rejects development/portable launches. A helper waits for this application's
PID to exit, rechecks the expected digest and starts the existing installer with UAC and silent-install
arguments. It does not kill other Java/Berill processes. The editor flushes its active draft before exit.
The helper restarts the application after completion or a cancelled/failed installation, and writes
errors to `~/.berill/updates/installation.log`. A failed installation is reported by the update center
on the next start; its package can be retried or discarded. MSI's existing rollback behavior is retained.

If the installed launcher is Authenticode-signed, the new installer must have a valid signature with
the same certificate thumbprint. Invalid signatures are rejected. The current release pipeline does
not provision a Windows signing certificate: unsigned builds rely on the explicitly configured HTTPS
update server and SHA-256. A checksum is an integrity check, not a publisher signature.

## Android installations

Packages stay in the app's private `cache/updates` directory. A non-exported FileProvider exposes
only that directory, with a temporary read grant for the system installer. Before opening it, Berill
checks package ID, version name, increasing version code and signing certificates (including a
single-signer rotation history). An APK signed with a different debug/release key is rejected.

Android always shows its own installation confirmation. If permission to install from Berill is
missing, the update button opens the relevant system settings. Return and press Install again after
granting permission. Cancelled system installation can be retried from the same verified package.

## Other platforms and verification

macOS/Linux downloads can be opened in their system package handler; this feature does not implement
an unattended DMG replacement or package-manager elevation. Windows portable ZIP replacement is not
implemented. The first client version containing the center must still be installed normally; future
versions can use it.

Automated checks cover download/hash validation, tampering, cancellation/partial cleanup, restart
restoration, legacy manifests, permission and exit state transitions, editor flush ordering, Windows
helper syntax and narrow-screen rendering. These tests do not install software or close the user's
running application. Confirm UAC/install/relaunch on a Windows test installation and APK signing,
permission grants and installation on an Android device before publishing a release.
