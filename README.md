# Schedule Parse Service

Сервис автоматически парсит расписание когда вы сохраняете его в указанный бакет MinIO в Excel формате. Предоставляет API для интеграции с [клиентом](https://github.com/simadimonyan/electronic-schedule-app) по защищенному соединению. Позволяет отслеживать состояние нагрузки системы в реальном времени.

![api](/docs/api.png)

## Стек технологий

1. Java 
2. Spring: Web, Data, Security, Actuator
3. ApachePOI
4. PostgreSQL
5. Redis
6. MinIO
7. Open Telemetry 
8. Micrometer
9. ClickHouse
10. Docker

## Установка и настройка

1. Клонируйте репозиторий:
    ```
    git clone https://github.com/simadimonyan/schedule-parse-system.git -b production
    cd schedule-parse-service
    ```

2. Отредактируйте `.env` и `application.properties`. Для production дополнительно настройте `grafana.ini` и `compose.yaml`. Следуйте комментариям в коде. При наличии доменного имени указывать его по url в соответствии с комментариями.
3. Если используется HTTP, закомментируйте строки для nginx в `compose.yaml`. Для HTTPS (если у вас нет сертификатов):

4. Создайте сертификаты через `bash init-certs.sh`

5. Отредактируйте конфигурационный файл journald (для предотвращения переполнения памяти диска):

```bash
sudo nano /etc/systemd/journald.conf
```

6. Найдите и измените (или добавьте) строку:

```ini
[Journal]
SystemMaxUse=100M
```

![сервис](./docs/service.png)

### Переменные окружения

Ниже приведены основные переменные окружения, используемые сервисом. Укажите их значения в файле `.env` или соответствующих конфигурациях.

| Переменная | Описание |
|------------|----------|
| `POSTGRES_USER` | Имя пользователя для подключения к PostgreSQL |
| `POSTGRES_PASSWORD` | Пароль пользователя PostgreSQL |
| `POSTGRES_DB` | Имя базы данных PostgreSQL |
| `REDIS_USER_PASSWORD` | Пароль для подключения к Redis |
| `MINIO_ROOT_USER` | Имя root-пользователя MinIO |
| `MINIO_ROOT_PASSWORD` | Пароль root-пользователя MinIO (должен содержать цифры, спецсимволы и буквы разных регистров) |
| `MINIO_WEBHOOK_AUTH_TOKEN` | Токен авторизации для вебхука MinIO (используется для интеграции с сервисом) |
| `PGADMIN_DEFAULT_EMAIL` | Email для входа в pgAdmin |
| `PGADMIN_DEFAULT_PASSWORD` | Пароль для входа в pgAdmin |
| `CLICKHOUSE_USER` | Имя пользователя ClickHouse |
| `CLICKHOUSE_PASSWORD` | Пароль пользователя ClickHouse |
| `HYPERDX_API_URL` | URL для подключения к HyperDX API (заменить на доменное имя в production) |
| `HYPERDX_API_PORT` | Порт HyperDX API |
| `HYPERDX_APP_URL` | URL для подключения к приложению HyperDX (заменить на доменное имя в production) |
| `HYPERDX_APP_PORT` | Порт приложения HyperDX |
| `FRONTEND_URL` | URL фронтенда (указать доменное имя в production) |
| `MINIO_SERVER_URL` | URL сервера MinIO |
| `MINIO_BROWSER_REDIRECT_URL` | URL для доступа к веб-интерфейсу MinIO (`/console` для production) |
| `GF_SECURITY_ADMIN_USER` | Имя администратора Grafana |
| `GF_SECURITY_ADMIN_PASSWORD` | Пароль администратора Grafana |

### Образ MinIO

Образ не тянется из Docker Hub — он лежит локально в `./images` и загружается скриптом.
Перед первым запуском (и после `docker system prune`):

```bash
./minio-image.sh load
```

Подробности — раздел [MinIO](#minio).

### Запуск

```bash
./minio-image.sh load
sudo docker compose up
```
При проблемах с правами доступа, где pgAdmin и grafana бесконечно перезапускаются, выполните:
```bash
sudo chmod -R 777 ./volumes
```

### Настройка MinIO и приложения

1. `docker exec -it minio /bin/sh`
2. `bash minio-generate-keys.sh` (адрес сервера: http://minio:9000)
3. Перейдите в `/src/main/resources`
4. Отредактируйте `application.properties` с новыми ключами.
5. Перезапустите приложение: `docker restart app`
6. Дождитесь запуска сервиса `app`
7. `docker exec -it minio /bin/sh`
8. `bash minio-manual-init-webhook.sh` (повторять с шага 7-8 при перезапуске app)

9. Для того, чтобы защитить сервер от переполнения памяти необходимо настроить первичные данные для скрипта перезагрузки и очистки сервисов (очищает volume clickhouse и кеш docker контейнеров):

```bash
# 1. Добавьте сгенирированные на шаге 2 MinIO ключи по переменным MINIO_ACCESS_KEY и MINIO_SECRET_KEY на строчках 11 и 12
# 2. Добавьте ваш URL с subpath к сервису для функции wait_for_service на строчках 146 и 147
nano restart-services.sh
```

10. Настройте автоочистку и перезапуск через crontab. Откройте файл crontab. Выполните следующую команду в терминале:

```bash
crontab -e
```

11. Это откроет файл crontab в текстовом редакторе. Добавьте строку. В самом низу файла добавьте команду для cron автоочистки и перезапуска сервисов каждые 12 часов (начиная с 01:00):
```
0 1,12 * * * /usr/bin/bash /root/schedule-parse-system/restart-services.sh  
```

### Настройка API

Инициализируйте четность недели через Swagger:
```
POST /api/v1/configuration/week/swap
Authorization: Bearer <access-token> (application.properties)

Body:
Bearer <admin-token> (application.properties)
```

### Настройка pgAdmin

![db](./docs/db.png)

- Подключитесь к базе данных через интерфейс pgAdmin. (адрес: app_db)

### Настройка HyperDX

![logs](./docs/logs.png)

- Измените доменное имя в `.env`.
- Подключите ClickHouse. (адрес: http://clickhouse:8123)
- При ошибках подключения к базе — перезапустите сервис `clickhouse`.

### Настройка Grafana для работы с subpath (production)

![monitor](./docs/monitoring.jpg)

1. Откройте файл `configs/grafana/grafana.ini`.
2. Найдите и отредактируйте параметры:
    - Установите параметр `root_url` с subpath `/grafana`
    - Убедитесь, что `serve_from_sub_path = true`.
3. Для production:
    - Откройте `compose.yaml`.
    - Удалите строки с портами, чтобы исключить прямой доступ и повысить безопасность соединений.

- Добавьте плагин ClickHouse.
- Добавьте datasource (адрес: clickhouse)
- Импортируйте дашборд из `/configs/grafana/MonitorDashboard.json`.
- При ошибках подключения — перезапустите сервис базы `clickhouse`.

## MinIO

Объектное хранилище, через которое в сервис попадает расписание: загружаешь `.xlsx`
в бакет `schedule` — MinIO дёргает вебхук приложения — парсер разбирает файл и
раскладывает пары по базе. Плюс это же хранилище отдаёт файлы обратно по API.

### Откуда берётся образ

Docker Hub репозиторий `minio/minio` закрыт: registry отвечает `401`, `docker pull`
не работает ни с тегом, ни с digest. Официальный источник образа теперь —
**quay.io/minio/minio**, а в проекте лежит его локальная копия, чтобы прод не зависел
от внешнего registry вовсе:

```
compose.yaml → image: local/minio:RELEASE.2025-09-07T16-13-09Z
               pull_policy: never        # ходить за образом некуда и незачем
images/minio-RELEASE.2025-09-07T16-13-09Z-amd64.tar.gz    64 МБ  ← сервер
images/minio-RELEASE.2025-09-07T16-13-09Z-arm64.tar.gz    59 МБ  ← Mac (Apple Silicon)
```

Архивы не в git. На новую машину их завозят руками:

```bash
scp images/minio-RELEASE.2025-09-07T16-13-09Z-amd64.tar.gz \
    root@server:/root/schedule-parse-system/images/
```

### Установка

```bash
./minio-image.sh load     # распаковать образ в docker (сам выберет amd64/arm64)
./minio-image.sh check    # что есть локально: образ, архивы, архитектура
docker compose up -d minio
docker logs minio | tail -5      # должно быть: API :9000, WebUI :9001
```

`load` идемпотентен: если образ уже в docker, он ничего не делает. Скрипт вызывается
из `restart-services.sh` автоматически — там перед стартом идёт `docker system prune -a`,
который образы стирает, а заново их взять неоткуда.

Если образа нет и архива нет (чистый сервер, забыли scp):

```bash
./minio-image.sh save     # тянет обе платформы с quay.io и кладёт в ./images
```

### Настройка

Переменные в `.env` (описаны в разделе [Переменные окружения](#переменные-окружения)):
`MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_WEBHOOK_AUTH_TOKEN`,
`MINIO_SERVER_URL`, `MINIO_BROWSER_REDIRECT_URL`.

Данные и сертификаты — на bind-mount'ах `./volumes/minio/data` и `./volumes/minio/certs`,
поэтому пересоздание контейнера и смена образа бакеты не трогают.

Ключи для приложения и вебхук настраиваются один раз — по шагам из
[Настройка MinIO и приложения](#настройка-minio-и-приложения):
`minio-generate-keys.sh` создаёт access/secret для пользователя, они прописываются в
`application.properties` (`minio.access.key`, `minio.secret.key`), затем
`minio-manual-init-webhook.sh` заводит бакет `schedule`, регистрирует
`notify_webhook:1` на `/minio-webhook` приложения и вешает событие `put`.

### Использование

Веб-интерфейс — `http://localhost:9001` (на проде `https://admin.myimsit.ru/console/`),
логин/пароль — `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`. Туда же кладут файл расписания,
если делают это руками.

Консольный клиент `mc` уже внутри образа — отдельно ставить не нужно:

```bash
docker exec -it minio /bin/sh
mc alias set myminio http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

mc ls myminio/schedule                     # что лежит в бакете
mc cp raspisanie.xlsx myminio/schedule/    # залить файл → сработает парсер
mc event list myminio/schedule             # вебхук на месте?
mc admin config get myminio notify_webhook # куда он стучится
mc admin info myminio                      # состояние хранилища
```

Событие приходит только на `put` в бакет `schedule`; всё остальное парсер игнорирует.

### Обновление образа

```bash
./minio-image.sh save    # поднимет свежий релиз с quay.io в ./images
```
Затем поменять тег `RELEASE.*` в `compose.yaml` и в шапке `minio-image.sh`,
загрузить и пересоздать контейнер:
```bash
./minio-image.sh load && docker compose up -d --force-recreate minio
```
Публично на quay сейчас лежат `RELEASE.2025-09-07T16-13-09Z` (последний обычный релиз)
и более свежие сборки с суффиксом `.hotfix.*`.

### Если что-то не так

| Симптом | Причина и что делать |
|---|---|
| `Error response from daemon: No such image: local/minio:…` | образ не загружен → `./minio-image.sh load` |
| `нет архива images/…-amd64.tar.gz` | архив не доехал на сервер → `scp` или `./minio-image.sh save` |
| `exec format error` при старте | загружен архив чужой платформы (arm64 на сервере) → взять `-amd64` |
| контейнер healthy, но расписание не парсится | слетел вебхук после перезапуска `app` → `bash minio-manual-init-webhook.sh` |
| `docker pull minio/minio` отвечает `401` | так и должно быть, Hub закрыт — образ берётся локально |

## URL

### Адреса для production (пример: myimsit.ru)

- HyperDX: https://admin.myimsit.ru
- MinIO: https://admin.myimsit.ru/console/
- pgAdmin: https://admin.myimsit.ru/pgadmin/
- Grafana: https://admin.myimsit.ru/grafana/
- API Swagger: https://api.myimsit.ru/schedule/
- Redis: tcp://myimsit.ru:6379

### Адреса для теста

- Swagger API: http://localhost:8080
- HyperDX: http://localhost:8081
- MinIO: http://localhost:9001
- Grafana: http://localhost:3000
- pgAdmin: http://localhost:15432
- Redis: http://localhost:6380
- PostgreSQL: http://localhost:5432

### Безопасность

- Запросы с одного IP: 10 r/s
- TCP соединения с одного IP: 2
- Кеширование ответов с кодом 200: 10 минут
- Время хранения логов, метрик и трассировок: 12 часов