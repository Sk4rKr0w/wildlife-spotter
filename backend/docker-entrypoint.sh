#!/bin/sh
set -eu

SSL_DIR="${SSL_DIR:-/app/data/ssl}"
SSL_CERT_PATH="${SSL_CERT_PATH:-$SSL_DIR/cert.pem}"
SSL_KEY_PATH="${SSL_KEY_PATH:-$SSL_DIR/key.pem}"
LE_ENABLED="${LE_ENABLED:-false}"
LE_DOMAINS="${LE_DOMAINS:-}"
LE_EMAIL="${LE_EMAIL:-}"
LE_STAGING="${LE_STAGING:-false}"
LE_DIR="${LE_DIR:-/app/data/letsencrypt}"
CLOUDFLARE_API_TOKEN="${CLOUDFLARE_API_TOKEN:-}"

if [ ! -f "$SSL_CERT_PATH" ] || [ ! -f "$SSL_KEY_PATH" ]; then
  mkdir -p "$SSL_DIR"
  if [ "$LE_ENABLED" = "true" ] && [ -n "$LE_DOMAINS" ] && [ -n "$LE_EMAIL" ] && [ -n "$CLOUDFLARE_API_TOKEN" ]; then
    mkdir -p "$LE_DIR"
    CLOUDFLARE_INI="$LE_DIR/cloudflare.ini"
    printf "dns_cloudflare_api_token = %s\n" "$CLOUDFLARE_API_TOKEN" > "$CLOUDFLARE_INI"
    chmod 600 "$CLOUDFLARE_INI"

    DOMAINS_ARGS=""
    OLD_IFS="$IFS"
    IFS=","
    for domain in $LE_DOMAINS; do
      domain="$(echo "$domain" | tr -d ' ')"
      if [ -n "$domain" ]; then
        DOMAINS_ARGS="$DOMAINS_ARGS -d $domain"
      fi
    done
    IFS="$OLD_IFS"

    STAGING_ARG=""
    if [ "$LE_STAGING" = "true" ]; then
      STAGING_ARG="--staging"
    fi

    certbot certonly \
      --non-interactive \
      --agree-tos \
      --email "$LE_EMAIL" \
      --dns-cloudflare \
      --dns-cloudflare-credentials "$CLOUDFLARE_INI" \
      --config-dir "$LE_DIR/config" \
      --work-dir "$LE_DIR/work" \
      --logs-dir "$LE_DIR/logs" \
      $STAGING_ARG \
      $DOMAINS_ARGS

    LIVE_DIR="$LE_DIR/config/live"
    PRIMARY_DOMAIN="$(echo "$LE_DOMAINS" | awk -F',' '{print $1}' | tr -d ' ')"
    if [ -n "$PRIMARY_DOMAIN" ] && [ -f "$LIVE_DIR/$PRIMARY_DOMAIN/fullchain.pem" ] && [ -f "$LIVE_DIR/$PRIMARY_DOMAIN/privkey.pem" ]; then
      cp "$LIVE_DIR/$PRIMARY_DOMAIN/fullchain.pem" "$SSL_CERT_PATH"
      cp "$LIVE_DIR/$PRIMARY_DOMAIN/privkey.pem" "$SSL_KEY_PATH"
    fi
  fi

  if [ ! -f "$SSL_CERT_PATH" ] || [ ! -f "$SSL_KEY_PATH" ]; then
    openssl req -x509 -newkey rsa:2048 -nodes \
      -keyout "$SSL_KEY_PATH" \
      -out "$SSL_CERT_PATH" \
      -days 365 \
      -subj "/CN=localhost"
  fi
fi

exec "$@"
