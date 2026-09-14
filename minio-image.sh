#!/usr/bin/env bash
# Локальная копия официального образа MinIO.
#
# Образ minio/minio с Docker Hub больше не отдаётся публично (репозиторий закрыт,
# registry отвечает 401), поэтому compose поднимает MinIO из локального тега
# local/minio:<RELEASE>, а сам тег берётся из tar-архива в ./images.
# Апстрим образа — quay.io/minio/minio, официальный registry MinIO.
#
# Архив лежит отдельно под каждую платформу: -amd64 (сервер), -arm64 (Mac).
# Нужный выбирается по архитектуре docker-демона на этой машине.
#
#   ./minio-image.sh load    загрузить образ из tar в docker (идемпотентно)
#   ./minio-image.sh save    скачать с quay.io и перезаписать оба архива (нужен интернет)
#   ./minio-image.sh check   показать, что есть локально
set -euo pipefail

cd "$(dirname "$0")"

RELEASE="RELEASE.2025-09-07T16-13-09Z"
UPSTREAM="quay.io/minio/minio:${RELEASE}"
LOCAL="local/minio:${RELEASE}"

host_arch() {
  docker version --format '{{.Server.Arch}}' 2>/dev/null \
    || case "$(uname -m)" in x86_64) echo amd64 ;; aarch64|arm64) echo arm64 ;; *) uname -m ;; esac
}

ARCH="$(host_arch)"
ARCHIVE="images/minio-${RELEASE}-${ARCH}.tar.gz"

image_arch() { docker image inspect "$LOCAL" --format '{{.Architecture}}' 2>/dev/null; }

case "${1:-load}" in
  load)
    if [ "$(image_arch)" = "$ARCH" ]; then
      echo "✓ образ $LOCAL ($ARCH) уже в docker"
      exit 0
    fi
    if [ ! -f "$ARCHIVE" ]; then
      echo "✗ нет архива $ARCHIVE (архитектура docker: $ARCH)" >&2
      echo "  забрать архив с рабочей машины (scp) или пересобрать: ./minio-image.sh save" >&2
      exit 1
    fi
    echo "Загружаю $LOCAL из $ARCHIVE ..."
    gunzip -c "$ARCHIVE" | docker load
    echo "✓ готово ($(image_arch))"
    ;;
  save)
    mkdir -p images
    keep=""
    for a in amd64 arm64; do
      echo "── $a ──"
      docker pull --platform "linux/$a" -q "$UPSTREAM"
      id="$(docker image inspect "$UPSTREAM" --format '{{.Id}}')"
      [ "$a" = "$ARCH" ] && keep="$id"
      docker tag "$id" "$LOCAL"
      docker save "$LOCAL" | gzip -1 > "images/minio-${RELEASE}-${a}.tar.gz"
    done
    # оставить локальным тегам образ родной платформы
    if [ -n "$keep" ]; then
      docker tag "$keep" "$LOCAL"
      docker tag "$keep" "$UPSTREAM"
    fi
    ls -lh images/
    ;;
  check)
    echo "release:  $RELEASE"
    echo "docker:   $ARCH"
    echo -n "образ:    "; image_arch >/dev/null && echo "$(docker image inspect "$LOCAL" --format '{{.Id}} ({{.Architecture}})')" || echo "нет"
    for a in amd64 arm64; do
      f="images/minio-${RELEASE}-${a}.tar.gz"
      echo -n "архив $a: "; [ -f "$f" ] && du -h "$f" | cut -f1 || echo "нет"
    done
    ;;
  *)
    echo "usage: $0 {load|save|check}" >&2
    exit 2
    ;;
esac
