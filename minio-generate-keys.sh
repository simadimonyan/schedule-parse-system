#!/bin/bash

echo "=== Автоматический генератор ключей доступа MinIO ==="

# Запрос только базовых данных
read -p "Введите адрес MinIO сервера: " MINIO_ENDPOINT
read -p "Введите alias для подключения (например, myminio): " ALIAS
read -p "Введите логин администратора MinIO: " MINIO_ROOT_USER
read -s -p "Введите пароль администратора MinIO: " MINIO_ROOT_PASSWORD
echo
read -p "Введите имя пользователя, для которого создаются ключи: " TARGET_USERNAME

echo
echo "Настройка алиаса и создание ключей..."

# Настройка алиаса для подключения к MinIO
mc alias set $ALIAS $MINIO_ENDPOINT $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD

# Создание ключей доступа БЕЗ политики, чтобы использовать права пользователя :cite[3]
echo "Генерация новых ключей доступа для пользователя '$TARGET_USERNAME'..."
mc admin accesskey create $ALIAS $TARGET_USERNAME
