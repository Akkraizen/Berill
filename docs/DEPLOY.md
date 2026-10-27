# Deployment

How the Berill production stack fits together, how to prepare the two servers and GitHub once, and
how to operate the services afterwards. Building and publishing app versions is covered in
[RELEASES.md](RELEASES.md).

## Contents

- [Overview](#overview)
- [Files and scripts](#files-and-scripts)
- [GitHub Actions workflows](#github-actions-workflows)
- [Server setup](#server-setup)
- [HTTPS and domains](#https-and-domains)
- [GitHub setup](#github-setup)
- [First deployment](#first-deployment)
- [Shipping a new version](#shipping-a-new-version)
- [Landing page](#landing-page)
- [Maintenance](#maintenance)
- [Troubleshooting](#troubleshooting)
- [Related documents](#related-documents)

## Overview

```text
                ┌──────── Server 1 (sync) ─────────────────────────────────┐
 Windows ─┐     │  Caddy (HTTPS, 443)                                      │
          ├────►│    sync.berill.space     → 127.0.0.1:8080 (sync)         │
 Android ─┤     │  docker compose (/opt/berill)                            │
          │     │    sync     — synchronization, tokens, /status           │
          │     │    db       — PostgreSQL 17, volume berill-db            │
          │     │    backup   — daily pg_dump → /opt/berill/backups        │
          │     └──────────────────────────────────────────────────────────┘
          │     ┌──────── Server 2 (updates) ──────────────────────────────┐
          └────►│  Caddy (HTTPS, 443)                                      │
                │    updates.berill.space  → 127.0.0.1:8081 (updates)      │
                │    berill.space          → /srv/berill.space (landing)   │
                │  docker compose (/opt/berill)                            │
                │    updates  — latest version and downloadable files      │
                │  /opt/berill/releases — APKs and installers (read-only)  │
                └──────────────────────────────────────────────────────────┘
       ▲                                         ▲
       │ images ghcr.io/<owner>/berill-sync      │ release files over SSH
       │        ghcr.io/<owner>/berill-updates   │ (server 2)
       └──────────── GitHub Actions ─────────────┘
```

| Component | Location | Purpose |
| --- | --- | --- |
| Desktop and Android apps | `app/`, `core/`, `core-api/`, `plugins/` | Clients. They work with a local SQLite database, synchronize through `sync`, and check `updates` for new versions. |
| Sync server | `server/` | Ktor + PostgreSQL: `/api/sync`, `/api/status`, the `/status` page, `/health`, access tokens. |
| Update server | `update-server/` | Separate Ktor application without a database: `/api/release` (latest version, readable from browsers), `/download/<file>`, `/download/latest/<windows\|macos\|android>` (redirect to the newest file, used by the landing page), `/health`. No token required. |
| Protocol | `protocol/` | Shared request and response models, version comparison. |
| Deployment | `deploy/` | `docker-compose.yml`, the `.env` template, the backup script, Gradle settings for Docker builds. |
| Landing page | separate `berill.space` project | Static Vue + Vite site. |

`sync` and `updates` are **two images, two servers, and two workflows**. Each service has its own
GitHub environment (`sync` and `updates`) with its own host and SSH key, so the services are
released, rolled back, and restarted independently; updating one never touches the other.

Both servers receive the same `docker-compose.yml`. `deploy-service.sh` starts only the requested
service, so server 1 runs `sync`, `db`, and `backup`, and server 2 runs only `updates`. The landing
page can be served by either server; the examples below put it on server 2.

## Files and scripts

### Servers

| File | Purpose |
| --- | --- |
| `server/Dockerfile` | Sync image: runs `:server` tests, builds the JAR, final image on JRE 21. |
| `update-server/Dockerfile` | Updates image: runs `:update-server` tests, builds `berill-updates.jar`, creates `/app/releases`. |
| `deploy/docker-compose.yml` | Services `db`, `sync` (`SYNC_IMAGE`), `updates` (`UPDATES_IMAGE`), `backup`. |
| `deploy/.env.example` | Settings template. The real `.env` lives only on the server and is never committed. |
| `deploy/backup/backup.sh` | Runs in the `backup` container: `pg_dump` on start (if there is no dump from the last 24 hours) and daily at `BACKUP_HOUR`; removes dumps older than `BACKUP_KEEP_DAYS`. |
| `deploy/gradle/*.gradle.kts` | Trimmed Gradle settings for Docker builds: `sync.settings` (protocol + server), `updates.settings` (protocol + update-server). No Android SDK required. |
| `server/src/main/resources/status.html` | The `/status` page: enter a token to see records and devices. |

### Environment variables

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `DATABASE_PASSWORD` | server 1 | — | PostgreSQL password. Not needed on server 2; Compose only prints a warning there. |
| `DATABASE_NAME`, `DATABASE_USER` | no | `berill` | Database and user name. |
| `DATABASE_VOLUME` | no | `berill-db` | Docker volume holding the database. Do not change it after the first start. |
| `HUB_TOKENS` | no | empty | Legacy token list (`user:token,...`). Prefer `token create`. |
| `SYNC_IMAGE` | no | `berill-sync:local` | Sync image. Written by CI. |
| `UPDATES_IMAGE` | no | `berill-updates:local` | Updates image. Written by CI. |
| `BIND_ADDRESS` | no | `0.0.0.0` | Address the ports are published on. Use `127.0.0.1` behind Caddy. |
| `SYNC_PORT` | no | `8080` | Host port for `sync`. |
| `UPDATES_PORT` | no | `8081` | Host port for `updates`. |
| `RELEASES_PATH` | no | `./releases` | Folder with APKs and installers, mounted read-only into `updates`. |
| `BACKUP_PATH` | no | `./backups` | Where backups are stored. |
| `BACKUP_KEEP_DAYS` | no | `14` | How many days to keep backups. |
| `BACKUP_HOUR` | no | `3` | Hour of the daily backup (in `TZ`). |
| `TZ` | no | `Europe/Moscow` | Time zone of the backup container. |

### Deployment scripts (`scripts/deploy/`)

| File | Runs on | Purpose |
| --- | --- | --- |
| `deploy-service.sh <sync\|updates> <image>` | Server of that service, invoked by CI over SSH | Writes `SYNC_IMAGE` or `UPDATES_IMAGE` to `.env`, runs `docker compose pull` and `up -d --no-build --remove-orphans` for that service only (plus `backup` for sync), waits up to 90 s for `/health`, prints the log on failure, prunes old images. |
| `publish-release.sh <folder>` | Server 2, invoked by CI over SSH | Moves uploaded files from `releases/.incoming` to `releases/`, keeps the latest `KEEP` versions. |

### Release scripts (`scripts/release/`, `scripts/windows-installer/`)

| File | Purpose |
| --- | --- |
| `release/version.ps1` | Reads and validates `version.properties`, checks that a `vX.Y.Z` tag matches it. |
| `release/prepare-android-signing.ps1` | Restores the Android signing key from a secret into a temporary folder in CI. |
| `release/checksums.ps1` | Writes `SHA256SUMS.txt` for `.exe`, `.apk`, and `.aab` files. |
| `windows-installer/build.ps1` | Builds the MSI and wraps it into the `Berill-<version>-Setup.exe` installer (Inno Setup). |
| `windows-installer/generate-assets.ps1` | Renders the installer icon from `BerillIcon.kt`. |
| `windows-installer/prepare-msi.ps1` | Prepares an MSI copy for testing an upgrade over an older version. |
| `windows-installer/Berill.iss`, `upgrade.iss` | Inno Setup scripts. |

### Maintenance

| File | Purpose |
| --- | --- |
| `scripts/update-dependency-locks.ps1` / `.sh` | Refreshes dependency lock files and `verification-metadata` after version changes in `libs.versions.toml`. |
| `.github/actions/ssh/action.yml` | Shared CI step: configures the `deploy` SSH connection from secrets. |

## GitHub Actions workflows

| Workflow | Trigger | Result |
| --- | --- | --- |
| **Sync server** (`sync-server.yml`) | Push to `master` touching `server/`, `protocol/`, `deploy/`, `gradle/`, `version.properties`; pull requests; manual | `berill-sync` image built with tests, pushed to GHCR as `<version>-<commit>`, `<version>`, `latest`, then deployed to server 1 (environment `sync`). Run manually with `image_tag` to roll back. |
| **Update server** (`update-server.yml`) | Same, for `update-server/` | `berill-updates` image, deployed to server 2 (environment `updates`). |
| `service.yml` | Called by the two workflows above | Shared logic: build, publish the image, copy `deploy/docker-compose.yml` and `backup.sh`, run `deploy-service.sh`, check the public URL. |
| **Release** (`release.yml`) | `v*` tag, or manually for a tag | Tests, signed APK and AAB, Windows installer, macOS DMG, `SHA256SUMS.txt`, a draft GitHub release. |
| **Publish update** (`publish-update.yml`) | GitHub release published; manually for a tag | Downloads the APK, the Windows installer, and the DMG (if present) from the release, verifies SHA-256, uploads them to `releases/` on server 2, checks the download through `UPDATES_URL`. |
| **macOS** (`macos.yml`) | Push to `master` touching app code; pull requests; manual | Desktop tests and the macOS DMG as an artifact. See [MACOS.md](MACOS.md). |
| **Prepare release** (`prepare-release.yml`) | Manual, with a version | Bumps `versionName` and `versionCode` (or keeps them when the version equals the current one), commits, tags, triggers **Release** and, with `deploy_servers`, both server workflows. |

## Server setup

Done once **on each server** unless a step says otherwise. The commands assume Ubuntu 24.04 and a
user with `sudo`.

### Docker and the deploy user

```sh
sudo apt update && sudo apt install -y ca-certificates curl
curl -fsSL https://get.docker.com | sudo sh

sudo adduser --disabled-password --gecos '' deploy
sudo usermod -aG docker deploy
sudo mkdir -p /opt/berill && sudo chown deploy:deploy /opt/berill
```

### SSH key for CI

Use a separate key per server, so that a leaked key exposes only one of them. On your computer:

```sh
ssh-keygen -t ed25519 -f berill-deploy-sync -C "github-actions berill sync" -N ""
ssh-keygen -t ed25519 -f berill-deploy-updates -C "github-actions berill updates" -N ""
```

Add the matching `.pub` file to `/home/deploy/.ssh/authorized_keys` on each server (mode `700` for
`.ssh`, `600` for the file). The private keys go into the `DEPLOY_SSH_KEY` secret of the matching
environment.

Take the host fingerprint for `known_hosts` **on the server itself** so it cannot be spoofed:

```sh
ssh-keyscan -t ed25519 localhost | sed 's/^localhost/<server-address>/'
```

### Firewall

```sh
sudo ufw allow OpenSSH
sudo ufw allow 80,443/tcp
sudo ufw enable
```

Do not open ports 8080 and 8081. Docker publishes ports past `ufw`, so they are
closed by `BIND_ADDRESS=127.0.0.1` in `.env`.

### Settings

Server 1 (sync):

```sh
sudo -iu deploy
cd /opt/berill
cat > .env <<'ENV'
DATABASE_PASSWORD=<long random password>
BIND_ADDRESS=127.0.0.1
TZ=Europe/Moscow
ENV
chmod 600 .env
mkdir -p backups backup
```

Server 2 (updates):

```sh
sudo -iu deploy
cd /opt/berill
cat > .env <<'ENV'
BIND_ADDRESS=127.0.0.1
TZ=Europe/Moscow
ENV
chmod 600 .env
mkdir -p releases
```

CI copies `docker-compose.yml` and `backup/backup.sh` on the first deployment.

### Migrating from the single-container layout

If an older installation with a single `server` container already runs on server 1:

1. Nothing has to be stopped. The `berill-db` volume is picked up because its name is set explicitly.
2. Run **Sync server**. The new `docker-compose.yml` replaces the old one, `--remove-orphans` removes
   the `server` container, and `sync` starts on the same port 8080 with the same data and tokens.
3. Prepare server 2 and run **Update server**.
4. The `BERILL_IMAGE` line in `.env` can be removed; it is no longer used.

If the server used to be built from source in another folder, run `docker compose down` there —
**without `-v`** — move the `.env` values to `/opt/berill/.env`, and continue as above.

## HTTPS and domains

Without HTTPS, tokens and data travel in plain text; end-to-end encryption protects only record
contents. Caddy obtains and renews Let's Encrypt certificates automatically.

DNS A records:

| Name | Points to |
| --- | --- |
| `sync.berill.space` | Server 1 |
| `updates.berill.space` | Server 2 |
| `berill.space`, `www.berill.space` | Server 2 (landing page) |

With Cloudflare, keep the records in **DNS only** mode until the certificates are issued.

Install Caddy on both servers:

```sh
sudo apt install -y caddy
```

Server 1, `/etc/caddy/Caddyfile`:

```text
sync.berill.space {
	encode zstd gzip
	reverse_proxy 127.0.0.1:8080
	request_body {
		max_size 64MB
	}
}
```

Server 2, `/etc/caddy/Caddyfile`:

```text
updates.berill.space {
	reverse_proxy 127.0.0.1:8081
}

berill.space, www.berill.space {
	root * /srv/berill.space
	encode zstd gzip
	try_files {path} /index.html
	file_server
	@assets path /assets/*
	header @assets Cache-Control "public, max-age=31536000, immutable"
}
```

On server 2, also create the landing folder:
`sudo mkdir -p /srv/berill.space && sudo chown deploy:deploy /srv/berill.space`.

```sh
sudo systemctl reload caddy
curl https://sync.berill.space/health
curl https://updates.berill.space/health
```

In the apps, the sync server is `https://sync.berill.space` (**Settings → Sync**) and the update
server is `https://updates.berill.space` (**Settings → Updates**; this is the default, set by
`berill.updatesUrl` in `gradle.properties`).

## GitHub setup

Done once.

1. **Settings → Environments**: create two environments, `sync` and `updates`. The deploy job of
   `service.yml` uses the environment named after the service; **Publish update** uses `updates`.

   Secrets:

   | Secret | Environment `sync` | Environment `updates` |
   | --- | --- | --- |
   | `DEPLOY_HOST` | Server 1 IP or domain. | Server 2 IP or domain. |
   | `DEPLOY_USER` | `deploy`. | `deploy`. |
   | `DEPLOY_SSH_KEY` | Private key `berill-deploy-sync`. | Private key `berill-deploy-updates`. |
   | `DEPLOY_KNOWN_HOSTS` | Server 1 line from [SSH key for CI](#ssh-key-for-ci). | Server 2 line. |

   Variables:

   | Variable | Environment | Default | Purpose |
   | --- | --- | --- | --- |
   | `SYNC_URL` | `sync` | — | `https://sync.berill.space`; checked after a sync deployment. |
   | `UPDATES_URL` | `updates` | — | `https://updates.berill.space`; checked after an updates deployment and after publishing a release. |
   | `RELEASES_KEEP` | `updates` | `3` | How many app versions to keep on server 2. |
   | `DEPLOY_PATH` | both, optional | `/opt/berill` | Deployment folder on the server. |
   | `DEPLOY_PORT` | both, optional | `22` | SSH port. |

   Enable **Required reviewers** on an environment to make its deployments wait for approval.
2. **Settings → Secrets and variables → Actions**: add the Android signing secrets described in
   [RELEASES.md](RELEASES.md#github-secrets).
3. **Settings → Actions → General → Workflow permissions**: select **Read and write permissions**
   (needed by **Prepare release**, which pushes a commit and a tag). If `master` is protected, allow
   GitHub Actions to push.
4. GHCR images stay private. During deployment the server logs in to the registry with the job's
   one-time token and logs out right after pulling.

## First deployment

1. Run **Actions → Sync server → Run workflow**, then **Update server → Run workflow** (or push to
   `master`).
2. Wait for the `image` and `deploy` jobs to pass in both. The image name is shown in the job summary.
3. Create tokens on server 1, one per device:

   ```sh
   cd /opt/berill
   docker compose run --rm sync token create igor --name pc
   docker compose run --rm sync token create igor --name phone
   docker compose run --rm sync token list igor
   ```

   A token is shown only once. Revoke one with `token revoke <id>`.
4. In the app, open **Settings → Sync**, enter the server address and the token, and save.
5. Open `https://sync.berill.space/status`: devices appear after their first synchronization.

## Shipping a new version

1. **Actions → Prepare release → Run workflow**, version `1.1.0`. The workflow bumps the version,
   tags `v1.1.0`, and starts the app and server builds.
2. **Release** creates a draft release with `Berill-1.1.0-Setup.exe`, `Berill-1.1.0-macos.dmg`, the
   APK, the AAB, and `SHA256SUMS.txt`. Check the files and click **Publish release**.
3. **Publish update** uploads the installer, the DMG, and the APK to server 2. The apps show "New version
   available" within 6 hours, or immediately after **Check for updates** in settings.

Without **Prepare release**: edit `version.properties`, commit, then run
`git tag v1.1.0 && git push --tags`. Details are in [RELEASES.md](RELEASES.md).

Each server also redeploys on its own when its code is pushed to `master`, without an app release.

## Landing page

Build the `berill.space` project:

```sh
npm ci
npm run build
```

Upload `dist/` to the folder Caddy serves on server 2:

```sh
rsync -av --delete dist/ deploy@<server-2>:/srv/berill.space/
```

On Windows without `rsync`: `scp -r dist/* deploy@<server-2>:/srv/berill.space/`. Caddy does not
need a restart; new files are served immediately.

## Maintenance

Run these in `/opt/berill` on the server named in the first column.

| Server | Task | Command |
| --- | --- | --- |
| both | Container status | `docker compose ps` |
| 1 | Sync log | `docker compose logs -f --tail 200 sync` |
| 2 | Updates log | `docker compose logs -f --tail 200 updates` |
| 1 | Backup log | `docker compose logs backup` |
| 1 | List backups | `ls -lh backups/` |
| 1 | Manual backup | `docker compose exec -T db pg_dump -U berill -Fc berill > backups/manual-$(date +%F).dump` |
| 1 | Restore a backup | `docker compose exec -T db pg_restore -U berill -d berill --clean --if-exists < backups/<file>.dump` |
| 1 / 2 | Restart | `docker compose restart sync` / `docker compose restart updates` |
| 2 | Versions being served | `ls releases/` or `curl -s 127.0.0.1:8081/api/release` |
| — | Roll back | **Actions → Sync server** or **Update server → Run workflow** with `image_tag` set to a GHCR tag |

Backups are stored on the same disk as the database. Copy `backups/` off server 1 weekly, for example
`rsync -av deploy@<server-1>:/opt/berill/backups/ ./berill-backups/`.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Deployment: `Set DEPLOY_HOST, DEPLOY_USER…` | The secrets of the `sync` or `updates` environment are missing. |
| Deployment: `Host key verification failed` | `DEPLOY_KNOWN_HOSTS` is wrong or belongs to the other server; take the line from the right server again. |
| Deployment: `No .env in /opt/berill` | `.env` has not been created (see [Settings](#settings)). |
| Deployment: `denied` on `docker compose pull` | The job cannot access the package: **Packages → `berill-sync` / `berill-updates` → Package settings** → grant the repository access. |
| `sync` does not answer `/health` | Check `docker compose logs sync`; usually `DATABASE_PASSWORD` does not match the existing database. |
| `updates` does not answer `/health` | Check `docker compose logs updates` and that `UPDATES_PORT` is free. |
| `port is already allocated` | An old server in another folder is still running; run `docker compose down` there (without `-v`). |
| App: "Server responded 401" | The token was revoked or mistyped; create a new one. |
| No update is offered | Wrong update server address in settings, no `Berill-<version>-android.apk` / `-Setup.exe` / `-macos.dmg` in `releases/` on server 2, or the version is not newer than the installed one. |
| Prepare release: `push declined` | Branch protection blocks GitHub Actions (see [GitHub setup](#github-setup), step 3). |

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [RELEASES.md](RELEASES.md) — versioning, signing, and building releases.
- [EXTERNAL_PLUGINS.md](EXTERNAL_PLUGINS.md) — writing third-party plugins.
- [MACOS.md](MACOS.md) — macOS builds.
