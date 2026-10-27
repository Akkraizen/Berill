# Releases

How Berill versions are numbered, how Android signing is configured, and how the release workflow
builds and publishes the Windows, macOS, and Android packages. Server deployment and the one-time server
setup are covered in [DEPLOY.md](DEPLOY.md).

## Contents

- [Versioning](#versioning)
- [Android signing key](#android-signing-key)
- [GitHub secrets](#github-secrets)
- [Local Android build](#local-android-build)
- [Creating a release](#creating-a-release)
- [What the workflow does](#what-the-workflow-does)
- [Publishing](#publishing)
- [Code signing](#code-signing)
- [Related documents](#related-documents)

## Versioning

The single source of the version is `version.properties` in the repository root:

```properties
versionName=1.0.1
versionCode=2
```

| Key | Format | Used by |
| --- | --- | --- |
| `versionName` | Three numbers, for example `1.0.2`. Windows limits: the first and second numbers are at most 255, the third at most 65535. | Windows, Android, servers. |
| `versionCode` | Integer from 1 to 2100000000. Increase it with every Android release. | Android. |

Never reuse the numbers of a published version. The application ID and the Windows `upgradeUuid` do
not change between regular updates.

## Android signing key

Every APK must be signed with the same key, otherwise it cannot be installed over a previous
version. Create the key once if you do not have one:

```powershell
keytool -genkeypair -v -keystore berill-release.jks -alias berill-release `
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep the `.jks` file, both passwords, and the alias in a safe place outside the repository and make a
separate backup. With Play App Signing, the Google Play upload key and the key used for the
distributed APK may differ.

## GitHub secrets

Open **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The `.jks` file encoded as Base64. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | Key alias set when the key was created. |
| `ANDROID_KEY_PASSWORD` | Key password. |

Copy the Base64 value to the clipboard:

```powershell
$keyPath = Read-Host 'Path to the .jks file'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | Set-Clipboard
```

Paste it only into the GitHub secret, then clear the clipboard. Base64 contains the private key and is
not encryption.

The workflow checks that all secrets are present, restores the key into a temporary runner folder,
and removes it in a final step. Keys and passwords never end up in the repository, artifacts, or the
checksum file.

## Local Android build

Create `keystore.properties` in the repository root. The file is ignored by Git:

```properties
storeFile=C:/Users/your-name/SigningKeys/berill-release.jks
storePassword=your-keystore-password
keyAlias=berill-release
keyPassword=your-key-password
```

Use `/` in paths. Alternatively, set the environment variables `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; they take precedence
over the file.

```powershell
.\gradlew.bat :app:androidApp:assembleRelease :app:androidApp:bundleRelease --no-configuration-cache
```

A release build without signing fails with a clear error. Debug builds still use the standard debug
key. Disable the configuration cache for signed builds so that signing parameters are not stored in
it.

## Creating a release

The recommended way is **Actions → Prepare release → Run workflow** with the version to release:

| Entered version | What the workflow does |
| --- | --- |
| Greater than `versionName`, for example `1.1.0` | Writes `versionName=1.1.0`, increments `versionCode`, commits to the default branch, tags `v1.1.0`. |
| Equal to `versionName`, for example the first `1.0.0` | Tags the current commit as `v1.0.0` without a bump; `versionCode` stays as is. |
| Lower than `versionName`, or the tag already exists | Fails without changing anything. |

Then it starts **Release** and, with `deploy_servers`, both server workflows. Run `git pull`
afterwards to get the version commit.

Manually:

1. Increase both values in `version.properties`.
2. Commit and push the change.
3. Create a tag that matches the version and push it:

   ```powershell
   git tag v1.0.1
   git push origin v1.0.1
   ```

The **Release** workflow runs on `v*` tags. When started manually from **Actions → Release → Run
workflow**, select an existing version tag; runs on a branch are rejected. The workflow must exist on
the default branch for manual runs to be available.

## What the workflow does

1. A Windows runner installs Java 21, Android SDK 37, WiX 3, and Inno Setup 7.1.0. The digital
   signature and publisher of the downloaded Inno Setup installer are verified before it runs.
2. JVM tests and server tests run.
3. Signed APK and AAB are built and their signatures verified.
4. The Windows MSI and the `Berill-<version>-Setup.exe` installer are built. The MSI version must
   match `version.properties`.
5. In parallel, a macOS runner runs the desktop tests and builds `Berill-<version>-macos.dmg`.
6. Checksums are computed after all files are built and verified again before the draft is created.
7. A **draft** GitHub release is created with:
   - `Berill-<version>-Setup.exe`;
   - `Berill-<version>-macos.dmg`;
   - `Berill-<version>-android.apk`;
   - `Berill-<version>-android.aab`;
   - `SHA256SUMS.txt`.

The build jobs need only `contents: read`; only the job that creates the draft gets
`contents: write`. No personal access token is needed: the built-in `GITHUB_TOKEN` is used. If an
organization policy forbids writing, allow it for the release workflow.

## Publishing

Publishing is manual. Open the draft in **Releases**, review the notes, test a fresh install and an
upgrade over the previous version with data preserved, then click **Publish release**.

Re-running the workflow updates files only on a draft; overwriting a published release is not
allowed.

Publishing triggers **Publish update**, which uploads the Windows installer, the APK, and the DMG to
the update server.
The apps then offer the new version within 6 hours, or immediately after **Check for updates** in
settings. See [DEPLOY.md](DEPLOY.md#shipping-a-new-version).

## Code signing

The Windows installer is not signed with a code signing certificate yet, so Windows SmartScreen shows
a warning on first launch. The publisher fields in Inno Setup and Gradle do not replace a signature.
Adding Windows code signing is a separate step once a certificate or a cloud signing service is
available. When it is added, sign the files first and compute SHA-256 afterwards.

The macOS DMG is not signed with a Developer ID certificate or notarized either, so Gatekeeper asks
for confirmation on first launch. Signing and notarization need the Apple Developer Program; see
[MACOS.md](MACOS.md#distribution).

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [DEPLOY.md](DEPLOY.md) — server setup, deployment, and maintenance.
- [EXTERNAL_PLUGINS.md](EXTERNAL_PLUGINS.md) — writing third-party plugins.
- [MACOS.md](MACOS.md) — macOS builds.
- [Windows installer](../scripts/windows-installer/README.md) — building the installer locally.
