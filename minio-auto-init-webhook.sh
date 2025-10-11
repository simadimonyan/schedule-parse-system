#!/bin/bash

echo "=== Start minio-init.sh ==="

# Use environment variables or prompt for input
if [ -z "$MODE" ]; then
    read -p "Выберите режим (prod/test) [prod]: " MODE
    MODE=${MODE:-prod}
fi

# Настройки по умолчанию для каждого режима
if [ "$MODE" = "prod" ]; then
    if [ -z "$ENDPOINT_PREFIX" ]; then
        read -p "Введите префикс для эндпоинта [/schedule]: " ENDPOINT_PREFIX
        ENDPOINT_PREFIX=${ENDPOINT_PREFIX:-/schedule}
    fi
    if [ -z "$BUCKET_NAME" ]; then
        read -p "Введите имя бакета [schedule]: " BUCKET_NAME
        BUCKET_NAME=${BUCKET_NAME:-schedule}
    fi
    if [ -z "$APP_HOST" ]; then
        read -p "Введите хост приложения (куда отправлять вебхук) [app]: " APP_HOST
        APP_HOST=${APP_HOST:-app}
    fi
    if [ -z "$APP_PORT" ]; then
        read -p "Введите порт приложения (куда отправлять вебхук) [8080]: " APP_PORT
        APP_PORT=${APP_PORT:-8080}
    fi
else
    ENDPOINT_PREFIX=${ENDPOINT_PREFIX:-""}
    BUCKET_NAME=${BUCKET_NAME:-"schedule"}
    APP_HOST=${APP_HOST:-"app"}
    APP_PORT=${APP_PORT:-"8080"}
fi

# Если MINIO_ACCESS_KEY пуст, запрашиваем ввод
if [ -z "$MINIO_ACCESS_KEY" ]; then
    read -p "Введите MinIO access key: " MINIO_ACCESS_KEY
fi

# Если MINIO_SECRET_KEY пуст, запрашиваем ввод 
if [ -z "$MINIO_SECRET_KEY" ]; then
    read -s -p "Введите MinIO secret key: " MINIO_SECRET_KEY
    echo
fi

echo "=== Конфигурация ==="
echo "Режим: $MODE"
echo "Бакет: $BUCKET_NAME"
echo "Вебхук endpoint: http://${APP_HOST}:${APP_PORT}${ENDPOINT_PREFIX}"
echo "===================="

# Alias set с логированием
echo "Выполняем: mc alias set myminio http://minio:9000 \"$MINIO_ACCESS_KEY\" \"******************\" --api S3v4"
mc alias set myminio http://minio:9000 "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" --api S3v4
if [ $? -ne 0 ]; then
    echo "ERROR: alias set failed"
    mc alias list
    mc --version
    exit 1
fi

# Создание бакета
echo "Выполняем: mc mb myminio/$BUCKET_NAME"
mc mb myminio/$BUCKET_NAME
if [ $? -ne 0 ]; then
    echo "WARNING: mb failed (возможно бакет уже существует)"
    mc ls myminio || echo "Cannot list alias myminio"
fi

# Проверяем существующие события на бакете
echo "Проверяем существующие события на бакете $BUCKET_NAME"
EXISTING_EVENTS=$(mc event list myminio/$BUCKET_NAME 2>/dev/null | wc -l)
if [ "$EXISTING_EVENTS" -gt 0 ]; then
    echo "Найдены существующие события. Очищаем..."
    mc event remove myminio/$BUCKET_NAME --force
    if [ $? -eq 0 ]; then
        echo "Существующие события успешно очищены"
    else
        echo "WARNING: Не удалось очистить события, продолжаем..."
    fi
fi

# Настройка вебхука с правильным endpoint
FULL_ENDPOINT="http://${APP_HOST}:${APP_PORT}${ENDPOINT_PREFIX}/minio-webhook"
echo "Настраиваем вебхук с endpoint $FULL_ENDPOINT"
mc admin config set myminio notify_webhook:1 endpoint="$FULL_ENDPOINT"
if [ $? -ne 0 ]; then
echo "ERROR: config set failed"
    exit 1
fi

# Перезагрузка конфигурации
echo "Перезагружаем конфигурацию MinIO"
mc admin service restart myminio --json
if [ $? -ne 0 ]; then
    echo "ERROR: service restart failed"
    exit 1
fi

# Ждем немного перед добавлением события
echo "Ждем запуска MinIO..."
sleep 5

# Добавление события для вебхука
echo "Выполняем: mc event add myminio/$BUCKET_NAME arn:minio:sqs::1:webhook --event put"
mc event add myminio/$BUCKET_NAME arn:minio:sqs::1:webhook --event put
if [ $? -ne 0 ]; then
    echo "ERROR: event add failed, пытаемся очистить и попробовать снова..."
    
    # Пробуем очистить события и попробовать еще раз
    mc event remove myminio/$BUCKET_NAME --force
    sleep 2
    mc event add myminio/$BUCKET_NAME arn:minio:sqs::1:webhook --event put
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Повторная попытка также не удалась"
        echo "Проверяем текущие события:"
        mc event list myminio/$BUCKET_NAME || echo "Cannot list events"
        exit 1
    fi
fi

# Проверка конфигурации
echo "=== Проверка конфигурации ==="
echo "Настройки вебхука:"
mc admin config get myminio notify_webhook
echo "События бакета:"
mc event list myminio/$BUCKET_NAME

echo "=== Инициализация завершена успешно ==="
echo "=== End minio-init.sh ==="

exec "$@"
