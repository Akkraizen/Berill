#!/usr/bin/env bash
set -euo pipefail

service="${1:?service name is required: sync or updates}"
image="${2:?image reference is required}"
cd "${DEPLOY_PATH:?DEPLOY_PATH is required}"

case "$service" in
  sync) variable=SYNC_IMAGE; port_variable=SYNC_PORT; default_port=8080; extra=(backup) ;;
  updates) variable=UPDATES_IMAGE; port_variable=UPDATES_PORT; default_port=8081; extra=() ;;
  *) echo "Unknown service: $service" >&2; exit 1 ;;
esac

if [ ! -f .env ]; then
  echo "No .env in $DEPLOY_PATH. Create it from deploy/.env.example (DATABASE_PASSWORD at least)." >&2
  exit 1
fi

tmp=$(mktemp)
grep -v "^$variable=" .env > "$tmp" || true
printf '%s=%s\n' "$variable" "$image" >> "$tmp"
cat "$tmp" > .env
rm -f "$tmp"

port=$(sed -n "s/^$port_variable=//p" .env | tail -n1)
port=${port:-$default_port}

mkdir -p releases backups backup
docker compose pull "$service" "${extra[@]}"
docker compose up -d --no-build --remove-orphans "$service" "${extra[@]}"

for _ in $(seq 1 45); do
  if curl -fsS "http://127.0.0.1:$port/health" > /dev/null 2>&1; then
    echo "$service is up: $image"
    docker image prune -f > /dev/null
    exit 0
  fi
  sleep 2
done

echo "$service did not become healthy" >&2
docker compose ps >&2
docker compose logs --tail 200 "$service" >&2
exit 1
