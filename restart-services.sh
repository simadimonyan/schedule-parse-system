#!/bin/bash

set -e

# Default values
MODE="prod"
ENDPOINT_PREFIX="/schedule"
BUCKET_NAME="schedule"
APP_HOST="app"
APP_PORT="8080"
MINIO_ACCESS_KEY="generated-access-key"
MINIO_SECRET_KEY="generated-secret-key"

# Function to display usage
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --mode MODE                  prod or test (default: prod)"
    echo "  --endpoint-prefix PREFIX     Endpoint prefix (default: /schedule)"
    echo "  --bucket-name NAME           Bucket name (default: schedule)"
    echo "  --app-host HOST              App host (default: app)"
    echo "  --app-port PORT              App port (default: 8080)"
    echo "  --minio-access-key KEY       MinIO access key"
    echo "  --minio-secret-key KEY       MinIO secret key"
    echo "  --help                       Show this help"
    exit 1
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --mode)
            MODE="$2"
            shift 2
            ;;
        --endpoint-prefix)
            ENDPOINT_PREFIX="$2"
            shift 2
            ;;
        --bucket-name)
            BUCKET_NAME="$2"
            shift 2
            ;;
        --app-host)
            APP_HOST="$2"
            shift 2
            ;;
        --app-port)
            APP_PORT="$2"
            shift 2
            ;;
        --minio-access-key)
            MINIO_ACCESS_KEY="$2"
            shift 2
            ;;
        --minio-secret-key)
            MINIO_SECRET_KEY="$2"
            shift 2
            ;;
        --help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

echo "=== Starting service restart and cleanup ==="

cd /root/schedule-parse-system/

# Stop all containers
echo "Stopping all Docker containers..."
docker stop $(docker ps -aq) 2>/dev/null || true

# Remove ClickHouse volume
echo "Removing ClickHouse volumes..."
rm -f -r volumes/clickhouse/ 2>/dev/null || true

# Clean Docker system
echo "Cleaning Docker system..."
docker system prune -a -f --volumes

# Start services
echo "Starting services with docker compose..."
docker compose up -d

# Wait for MinIO and App services to be healthy
echo "Waiting for MinIO and App services to be ready..."

# Функция для проверки готовности сервиса
wait_for_service() {
    local service=$1
    local url=$2
    local max_attempts=30
    local attempt=1
    
    echo "Waiting for $service to be ready at $url..."
    
    while [ $attempt -le $max_attempts ]; do
        # Проверяем, запущен ли контейнер
        if docker compose ps "$service" | grep -q "Up"; then
            # Получаем HTTP статус-код
            local http_code
            http_code=$(curl -s -o /dev/null -w "%{http_code}" -L --connect-timeout 10 "$url")
            
            # Проверяем статус-код: 2xx и 3xx считаем успехом
            if [[ "$http_code" =~ ^[23][0-9]{2}$ ]]; then
                echo "$service is up and running (HTTP $http_code)"
                return 0
            else
                echo "$service returned HTTP $http_code (attempt $attempt/$max_attempts)"
                
                # Для разных типов ошибок выводим дополнительную информацию
                if [[ "$http_code" =~ ^5[0-9]{2}$ ]]; then
                    echo "Server error (502, 503, etc.) - service is starting up..."
                elif [[ "$http_code" =~ ^4[0-9]{2}$ ]]; then
                    echo "Client error (404, 403, etc.) - path might be incorrect"
                elif [ -z "$http_code" ]; then
                    echo "No response (connection refused/timeout)"
                fi
            fi
        else
            echo "Container $service is not running (attempt $attempt/$max_attempts)"
        fi
        
        echo "Waiting for $service... (attempt $attempt/$max_attempts)"
        sleep 20
        attempt=$((attempt + 1))
    done
    
    echo "ERROR: $service failed to start within expected time"
    
    # Дополнительная диагностика при неудаче
    echo "=== Final diagnostic ==="
    echo "Container status:"
    docker compose ps "$service"
    echo "Last curl attempt:"
    curl -v -L --connect-timeout 5 "$url" 2>&1 | head -20
    echo "======================="
    
    return 1
}

# Изменить url под ваше доменное имя или IP (с учетом subpath)
wait_for_service "minio" "https://schedule-imsit.ru/console"
wait_for_service "app" "https://schedule-imsit.ru/schedule"

# Additional wait to ensure services are fully initialized
echo "Waiting for services to fully initialize..."
sleep 20

# Execute minio initialization script with parameters
echo "Initializing MinIO with webhook configuration..."
docker exec -i minio /bin/bash -c "
export MODE='$MODE'
export ENDPOINT_PREFIX='$ENDPOINT_PREFIX'
export BUCKET_NAME='$BUCKET_NAME'
export APP_HOST='$APP_HOST'
export APP_PORT='$APP_PORT'
export MINIO_ACCESS_KEY='$MINIO_ACCESS_KEY'
export MINIO_SECRET_KEY='$MINIO_SECRET_KEY'
bash minio-auto-init-webhook.sh
"

echo "=== Service restart and initialization completed successfully ==="


