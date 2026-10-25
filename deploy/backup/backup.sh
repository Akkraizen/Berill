#!/bin/sh
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
HOUR="${BACKUP_HOUR:-3}"
DIR=/backups

backup() {
  name="$DIR/berill-$(date +%Y-%m-%d_%H%M).dump"
  if pg_dump --format=custom --file="$name.tmp"; then
    mv "$name.tmp" "$name"
    echo "$(date '+%F %T') backup ok: $name ($(du -h "$name" | cut -f1))"
  else
    rm -f "$name.tmp"
    echo "$(date '+%F %T') backup FAILED" >&2
  fi
  find "$DIR" -maxdepth 1 -name 'berill-*.dump' -mtime +"$((KEEP_DAYS - 1))" -print -delete
}

mkdir -p "$DIR"
if [ -z "$(find "$DIR" -maxdepth 1 -name 'berill-*.dump' -mtime -1)" ]; then
  backup
fi

while true; do
  now=$(date +%s)
  next=$(date -d "today $HOUR:00" +%s)
  [ "$next" -le "$now" ] && next=$(date -d "tomorrow $HOUR:00" +%s)
  sleep $((next - now))
  backup
done
