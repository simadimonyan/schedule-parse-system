#!/bin/bash

echo "=== Start minio-init.sh ==="

# Если MINIO_ACCESS_KEY пуст, запрашиваем ввод
if [ -z "$MINIO_ACCESS_KEY" ]; then
  read -p "Введите MinIO access key: " MINIO_ACCESS_KEY
fi

# Если MINIO_SECRET_KEY пуст, запрашиваем ввод 
if [ -z "$MINIO_SECRET_KEY" ]; then
  read -s -p "Введите MinIO secret key: " MINIO_SECRET_KEY
  echo
fi

echo "Using ACCESS_KEY='$MINIO_ACCESS_KEY', SECRET_KEY='******'"

# Alias set с логированием
echo "Выполняем: mc alias set myminio http://localhost:9000 \"$MINIO_ACCESS_KEY\" \"******************\" --api S3v4"
mc alias set myminio http://localhost:9000 "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" --api S3v4
if [ $? -ne 0 ]; then
  echo "ERROR: alias set failed"
  mc alias list
  mc --version
fi

# Создание бакета
echo "Выполняем: mc mb myminio/schedule"
mc mb myminio/schedule
if [ $? -ne 0 ]; then
  echo "ERROR: mb failed"
  mc ls myminio || echo "Cannot list alias myminio"
fi

# Добавление события для вебхука
echo "Выполняем: mc event add myminio/schedule arn:minio:sqs::1:webhook --event put"
mc event add myminio/schedule arn:minio:sqs::1:webhook --event put
if [ $? -ne 0 ]; then
  echo "ERROR: event add failed"
  mc event list myminio/schedule || echo "Cannot list events"
fi

echo "=== End minio-init.sh ==="

exec "$@"