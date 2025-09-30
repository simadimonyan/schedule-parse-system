# Schedule Parse Service

## Описание

Schedule Parse Service — это сервис для парсинга расписаний и интеграции с облачным хранилищем MinIO. Сервис автоматически обрабатывает расписания и сохраняет результаты в указанный бакет MinIO.

## Установка и настройка

1. Клонируйте репозиторий:
    ```bash
    git clone <repo-url>
    cd schedule-parse-service
    ```

2. Предварительно настройте файлы `application.properties` и `.env`:
    - Укажите параметры подключения к MinIO и другие необходимые настройки.

3. Разверните сервис и MinIO с помощью Docker Compose:
    ```bash
    docker-compose up -d
    ```

## Настройка MinIO

1. Запустите MinIO через Docker Compose.
2. Создайте бакет вручную через веб-интерфейс или CLI.
3. Сгенерируйте ключи доступа (Access Key и Secret Key) для подключения к MinIO через интерфейс.
4. Добавьте полученные ключи и имя бакета в `application.properties`:
    ```
    minio.endpoint=<minio-host>:<port>
    minio.access-key=<your-access-key>
    minio.secret-key=<your-secret-key>
    minio.bucket=<your-bucket-name>
    ```

5. Для настройки вебхука используйте скрипт `minio-init.sh`, передав ему ключи доступа и имя бакета после их создания:
    ```bash
    docker exec -it <minio-container-name> bash
    ./minio-init.sh <your-access-key> <your-secret-key> <your-bucket-name>
    ```

## Запуск

После настройки и запуска MinIO, убедитесь, что ключи и параметры указаны в конфиге, затем запустите сервис:
```bash
docker exec -it <service-container-name> java -jar schedule-parse-service.jar
```

## Использование

Сервис автоматически парсит расписания и сохраняет их в указанный бакет MinIO. Для интеграции с другими сервисами используйте вебхук, указав соответствующие параметры подключения.

## Контакты

Для вопросов и поддержки обращайтесь к разработчику.

