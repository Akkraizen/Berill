# Attachment storage and off-server backups

Local attachment storage remains the default. No cloud account or bucket is provisioned automatically.
The same authenticated blob API is used by old and new clients. Payloads remain end-to-end encrypted
when the client enables encryption; the server does not decrypt them. Buckets must remain private.

## Configure S3-compatible attachment storage

Create a private bucket with your provider. In `deploy/.env`, set:

```dotenv
ATTACHMENTS_BACKEND=s3
ATTACHMENTS_S3_ENDPOINT=https://s3.example.com
ATTACHMENTS_S3_BUCKET=berill-attachments
ATTACHMENTS_S3_PREFIX=attachments
ATTACHMENTS_S3_PATH_STYLE=true
AWS_REGION=us-east-1
ATTACHMENTS_AWS_ACCESS_KEY_ID=<attachment-account-key>
ATTACHMENTS_AWS_SECRET_ACCESS_KEY=<attachment-account-secret>
```

For AWS S3, the endpoint can be omitted. Use your provider's region. Temporary credentials can use
`ATTACHMENTS_AWS_SESSION_TOKEN`. The server uses the AWS SDK default credential chain; deployment
can instead provide a workload role. The backup mirror currently requires the explicit attachment
credentials above. Use HTTPS for external endpoints. Plain HTTP is supported for local development.

The attachment identity needs GetObject and PutObject under the configured prefix; the backup mirror
also needs ListBucket for that prefix. The app has no direct bucket credentials or public object URLs.
Objects are named `<prefix>/<SHA256(user-id)>/<attachment-hash>`; existing per-user isolation is preserved.

```sh
docker compose up -d --build sync backup
```

Old files in `berill-attachments` remain readable when S3 returns NoSuchKey. A permissions, network,
or missing-bucket error is reported as a server error, not a missing photo. New uploads must succeed
in S3 before the API acknowledges them. Local fallback is retained and never automatically deleted.
Clients should retry failed operations. A successful retry overwrites the same deterministic object key.

## Encrypted backups in a separate bucket

Create a second private bucket, preferably in a separate account/provider. Configure independent
backup credentials; the unprefixed AWS variables belong only to restic:

```dotenv
RESTIC_REPOSITORY=s3:https://s3.example.com/berill-backups/repository
RESTIC_PASSWORD=<long-unique-password>
AWS_ACCESS_KEY_ID=<backup-account-key>
AWS_SECRET_ACCESS_KEY=<backup-account-secret>
BACKUP_HOUR=3
BACKUP_KEEP_DAYS=14
RESTIC_KEEP_DAILY=14
```

Initialize once, then start the scheduler:

```sh
docker compose build backup
docker compose run --rm --no-deps --entrypoint restic backup init
docker compose up -d backup
docker compose logs backup
# Optional immediate backup without waiting for the scheduled hour:
docker compose run --rm -e BACKUP_ONCE=1 backup
docker compose run --rm --no-deps --entrypoint restic backup snapshots
docker compose run --rm --no-deps --entrypoint restic backup check
```

The scheduler writes a PostgreSQL dump, copies S3 blobs into a persistent local mirror, merges legacy
local blobs, creates a tar archive, and sends the dump and archive to restic. Copying does not delete
blobs; historical data remains available for restore. An S3/restic failure retains local data and skips
retention cleanup. The password is not recoverable: keep it outside this server. Backup credentials
need list/read/write/delete access for repository maintenance. Do not put credentials in Git.

Database and blobs are separate snapshots. Blobs are uploaded before records synchronize and are
not automatically garbage-collected. During a live backup, newer blobs may be included harmlessly;
for a strict point-in-time restore set, pause client writes while taking the backup.

## Restore and rollback

Restore a selected restic snapshot into a new directory, never over the live database:

```sh
docker compose run --rm --no-deps --entrypoint restic backup restore <snapshot-id> --target /backups/restore
```

Find its `.dump` and `.dump.attachments.tar`. Restore the dump with `pg_restore` into an empty database.
Extract the archive into the attachment volume: it contains `<user-hash>/<blob-hash>` entries, not
the S3 prefix. Start with `ATTACHMENTS_BACKEND=local` to verify the restore. Upload that directory to
the configured private S3 prefix using `rclone copy` if restoring directly to S3.

Before rolling an existing S3 deployment back to local, stop client writes, take a successful backup,
and copy its complete attachment archive into `berill-attachments`. Only then switch the backend to
local. Merely changing the flag cannot recover objects created only in S3. Do not remove the old
volume or bucket until the restored database and attachment downloads have been verified.

The deployment must have enough local disk space for the S3 mirror, attachment archive, dumps,
and retained backups. This preparation does not claim an external backup exists until a real
repository is configured and a snapshot/check succeeds.
