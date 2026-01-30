FROM node:20-bookworm-slim

WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    ca-certificates \
    openssl \
    python3 \
    make \
    g++ \
    certbot \
    python3-certbot-dns-cloudflare \
  && rm -rf /var/lib/apt/lists/*

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY . .

RUN chmod +x /app/docker-entrypoint.sh
RUN chmod +x /app/letsencrypt-renew.sh

ENV NODE_ENV=production
ENV PORT=443
ENV SSL_DIR=/app/data/ssl
ENV SSL_CERT_PATH=/app/data/ssl/cert.pem
ENV SSL_KEY_PATH=/app/data/ssl/key.pem

EXPOSE 443

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["node", "index.js"]
