#!/bin/bash

set -e

CERT_DIR="./volumes/certbot/conf/live"
DOMAINS=("myimsit.ru" "api.myimsit.ru" "admin.myimsit.ru")
EMAIL="${CERTBOT_EMAIL:-admin@myimsit.ru}"
WEBROOT="/var/www/certbot"

echo "=== Checking SSL certificates ==="

# Проверяем, нужна ли инициализация сертификатов
NEED_INIT=false
for domain in "${DOMAINS[@]}"; do
    if [ ! -f "$CERT_DIR/$domain/fullchain.pem" ]; then
        echo "Certificate for $domain not found"
        NEED_INIT=true
        break
    fi
done

if [ "$NEED_INIT" = false ]; then
    echo "All certificates already exist, skipping initialization"
    exit 0
fi

echo "=== First-time certificate initialization ==="

# Создаём директории
mkdir -p ./volumes/certbot/www
mkdir -p ./volumes/certbot/conf

# Запускаем nginx только с HTTP конфигом (без SSL)
echo "Starting nginx with HTTP-only config..."
docker rm -f nginx-init 2>/dev/null || true
docker run -d \
    --name nginx-init \
    --network host \
    -v "$(pwd)/configs/nginx/nginx-init.conf:/etc/nginx/nginx.conf:ro" \
    -v "$(pwd)/volumes/certbot/www:$WEBROOT:ro" \
    nginx:alpine

echo "Waiting for nginx to start..."
sleep 5

# Получаем сертификаты для всех доменов
echo "Requesting certificates from Let's Encrypt..."
docker run --rm \
    --network host \
    -v "$(pwd)/volumes/certbot/www:$WEBROOT" \
    -v "$(pwd)/volumes/certbot/conf:/etc/letsencrypt" \
    certbot/certbot certonly \
    --webroot \
    --webroot-path "$WEBROOT" \
    -d myimsit.ru \
    -d api.myimsit.ru \
    -d admin.myimsit.ru \
    --email "$EMAIL" \
    --agree-tos \
    --non-interactive \
    --no-eff-email

# Останавливаем временный nginx
echo "Stopping temporary nginx..."
docker stop nginx-init && docker rm nginx-init

echo "=== Certificate initialization completed ==="
