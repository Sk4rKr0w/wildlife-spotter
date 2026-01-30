#!/bin/sh
set -eu

LE_ENABLED="${LE_ENABLED:-false}"
LE_DIR="${LE_DIR:-/app/data/letsencrypt}"
CLOUDFLARE_API_TOKEN="${CLOUDFLARE_API_TOKEN:-}"

if [ "$LE_ENABLED" != "true" ]; then
  echo "LE_ENABLED is false; skipping renew loop"
  exit 0
fi

if [ -z "$CLOUDFLARE_API_TOKEN" ]; then
  echo "CLOUDFLARE_API_TOKEN is missing; cannot renew"
  exit 1
fi

mkdir -p "$LE_DIR"
CLOUDFLARE_INI="$LE_DIR/cloudflare.ini"
printf "dns_cloudflare_api_token = %s\n" "$CLOUDFLARE_API_TOKEN" > "$CLOUDFLARE_INI"
chmod 600 "$CLOUDFLARE_INI"

while true; do
  certbot renew \
    --dns-cloudflare \
    --dns-cloudflare-credentials "$CLOUDFLARE_INI" \
    --config-dir "$LE_DIR/config" \
    --work-dir "$LE_DIR/work" \
    --logs-dir "$LE_DIR/logs"
  sleep 12h
done
