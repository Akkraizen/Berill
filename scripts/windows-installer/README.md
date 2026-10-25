# Windows EXE installer

The branded Inno Setup wizard embeds the release MSI. Windows Installer owns
the application files, shortcuts, updates, and uninstall entry; the wrapper
does not register a second uninstaller. Java is bundled in the MSI.

Requirements: the project's JDK/Gradle toolchain and Inno Setup 7.1 or newer.

From the repository root:

```powershell
.\scripts\windows-installer\build.ps1 -Compiler 'C:\Program Files\Inno Setup 7\ISCC.exe'
```

The application version comes from `version.properties` in the repository root.
Automated releases and Android signing are documented in [docs/RELEASES.md](../../docs/RELEASES.md).

The script builds `:app:desktopApp:packageReleaseMsi`, reads the version and
product code directly from that MSI, and compiles `Berill-<version>-Setup.exe`
into `app/desktopApp/build/compose/binaries/main-release/branded-exe`.
Pass `-SkipMsiBuild` to package the latest existing release MSI or
`-OutputDirectory` to change the output folder. Pass `-MsiPath` to package an exact MSI from an isolated build without rebuilding it.

The wizard runs as administrator because the MSI installs for all users.
It forwards INSTALLDIR and JP_INSTALL_DESKTOP_SHORTCUT, always creates a Start
menu shortcut, and repairs an already installed MSI with the same ProductCode.
If that product has an InstallLocation, the existing location is kept.
Exit code 3010 requests a restart; other MSI errors stop the wizard instead
of displaying success. MSI logs remain in `%LOCALAPPDATA%\Berill\Installer Logs`.
Exit Berill through its tray menu before updating.

`build.ps1` regenerates assets on every run. `generate-assets.ps1` reads the paths and colors from the current Compose `BerillIcon.kt`, clips all layers to the rounded silhouette, and produces a transparent PNG plus an ICO with nine sizes (16 through 256 pixels). The same ICO is used by the Windows application package. No banner assets are generated.

For UI-only checks, `-Preview` compiles a separate test build that supports
`/CURRENTUSER` and blocks the Install button. Never distribute that build.
The normal build requires administrator rights and enables installation.

The EXE is currently unsigned. Production signing requires the publisher's
code-signing certificate and must be added separately.

Validation: release MSI and production EXE compile successfully. A preview
was opened on Windows; full installation/upgrade/uninstall testing remains
to be done on a disposable Windows machine. The visual inspection was
interrupted before all wizard pages were reviewed.

The wizard supports English and Russian, detects the Windows UI language, and offers a language selector. All custom labels, progress text, and error messages use translated messages. Welcome and completion pages use the full width without a side banner.

The English welcome page was visually checked. Further UI inspection was stopped by the user with Escape. Full installation, update, and removal remain to be checked on a disposable Windows machine.

Updates retain the original Windows UpgradeCode `037C9DEC-C551-3B2F-B65E-D0959A559736`.
Release 1.0.1 upgrades existing 1.0.0 installations through the MSI major-upgrade
transaction. The wrapper discovers installed versions by UpgradeCode rather than
only the new ProductCode, keeps the existing installation directory, and uses
REINSTALL=ALL/REINSTALLMODE=vomus only when the same ProductCode is already installed.
It does not uninstall the old package in a separate step or touch user databases.
A newer installed version blocks a downgrade with a localized message.
Increment packageVersion for future releases while keeping upgradeUuid unchanged.

`-CheckUpgrade` creates a non-installing diagnostic build. Running it with
`/VERYSILENT /CURRENTUSER /LOG=...` checks install/repair/upgrade/downgrade decisions,
queries the existing Berill installation, logs UPGRADE_CHECK_PASS, and exits before
showing the wizard. Exit code 1 is expected because setup is intentionally aborted.
Never distribute that diagnostic build. Full update/rollback validation should be
performed on a disposable Windows machine; the read-only check preserves the
user's running installation.
The EXE embeds a separate MSI prepared by prepare-msi.ps1. RemoveExistingProducts is scheduled immediately after InstallInitialize and before ProcessComponents, so MSI can roll back removal if the update fails. The raw jpackage MSI is preserved unchanged. Each build uses a unique staging folder under app/desktopApp/build/windows-installer-msi to avoid file locks across builds. Sign the prepared package only after this step.
