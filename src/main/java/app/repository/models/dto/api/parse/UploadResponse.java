package app.repository.models.dto.api.parse;

/**
 * Итог загрузки файла расписания в бакет.
 *
 * @param file    имя объекта в бакете; {@code null} — файл не сохранён
 * @param parsing поставлен ли файл в разбор
 * @param message что произошло — этот текст показывается человеку
 */
public record UploadResponse(String file, boolean parsing, String message) {}
