#!/bin/sh
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
HOUR="${BACKUP_HOUR:-3}"
DIR=/backups

backup() {
  name="$DIR/berill-$(date +%Y-%m-%d_%H%M).dump"
  if pg_dump --format=custom --file="$name.tmp"; then
    mv "$name.tmp" "$name"
    attachments=/attachments
    if [ "${ATTACHMENTS_BACKEND:-local}" = s3 ]; then
      # Dedicated attachment credentials; restic can use an independent account/bucket.
      export RCLONE_CONFIG_ATTACHMENTS_TYPE=s3
      export RCLONE_CONFIG_ATTACHMENTS_PROVIDER=Other
      export RCLONE_CONFIG_ATTACHMENTS_ACCESS_KEY_ID="${ATTACHMENTS_AWS_ACCESS_KEY_ID:?Required for attachment backup}"
      export RCLONE_CONFIG_ATTACHMENTS_SECRET_ACCESS_KEY="${ATTACHMENTS_AWS_SECRET_ACCESS_KEY:?Required for attachment backup}"
      export RCLONE_CONFIG_ATTACHMENTS_SESSION_TOKEN="${ATTACHMENTS_AWS_SESSION_TOKEN:-}"
      export RCLONE_CONFIG_ATTACHMENTS_REGION="${AWS_REGION:-us-east-1}"
      export RCLONE_CONFIG_ATTACHMENTS_ENDPOINT="${ATTACHMENTS_S3_ENDPOINT:-}"
      export RCLONE_CONFIG_ATTACHMENTS_FORCE_PATH_STYLE="${ATTACHMENTS_S3_PATH_STYLE:-true}"
      attachments=/backups/attachment-cache
      mkdir -p "$attachments"
      # Copy without deleting: keep historical blobs and legacy files for restores.
      cp -a /attachments/. "$attachments/" || return 1
      rclone copy "attachments:${ATTACHMENTS_S3_BUCKET:?Required}/${ATTACHMENTS_S3_PREFIX:-attachments}" "$attachments" || return 1
    fi
    if ! tar -C "$attachments" -cf "$name.attachments.tar.tmp" .; then
      rm -f "$name.attachments.tar.tmp"
      return 1
    fi
    mv "$name.attachments.tar.tmp" "$name.attachments.tar"
    if [ -n "${RESTIC_REPOSITORY:-}" ]; then
      # Initialize explicitly once; authentication/network failures must not trigger init.
      restic cat config >/dev/null || return 1
      restic backup --host berill --tag berill "$name" "$name.attachments.tar" || return 1
      restic forget --host berill --tag berill --group-by host,tags --keep-daily "${RESTIC_KEEP_DAILY:-14}" --prune || return 1
    fi
    echo "$(date '+%F %T') backup ok: $name ($(du -h "$name" | cut -f1))"
  else
    rm -f "$name.tmp"
    echo "$(date '+%F %T') backup FAILED" >&2
    return 1
  fi
  find "$DIR" -maxdepth 1 -name 'berill-*.dump*' -mtime +"$((KEEP_DAYS - 1))" -print -delete
}

case "$HOUR" in ''|*[!0-9]*) echo "Invalid BACKUP_HOUR" >&2; exit 1;; esac
[ "$HOUR" -le 23 ] || exit 1
case "$KEEP_DAYS" in ''|*[!0-9]*) echo "Invalid BACKUP_KEEP_DAYS" >&2; exit 1;; esac
[ "$KEEP_DAYS" -ge 1 ] || exit 1
mkdir -p "$DIR"
if [ "${BACKUP_ONCE:-0}" = 1 ]; then
  backup
  exit $?
fi
if [ -n "${RESTIC_REPOSITORY:-}" ] || [ -z "$(find "$DIR" -maxdepth 1 -name 'berill-*.dump' -mtime -1)" ]; then
  backup || echo "Backup failed; local dumps retained" >&2
fi

while true; do
  now=$(date +%s)
  next=$(date -d "today $HOUR:00" +%s)
  [ "$next" -le "$now" ] && next=$(date -d "tomorrow $HOUR:00" +%s)
  sleep $((next - now))
  backup || echo "Backup failed; local dumps retained" >&2
done
