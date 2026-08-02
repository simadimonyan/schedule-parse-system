package app.repository.models.dto.api.parse;

/**
 * Итог постановки файла в разбор.
 *
 * @param file    какой файл взят; {@code null} — не нашлось ни названного, ни последнего
 * @param message что произошло
 */
public record ParseResponse(String file, String message) {}
