package app.controller.exceptions;

/** Запрос корректен, но противоречит текущему состоянию данных (ответ 409) */
public class ConflictException extends Exception {

    public ConflictException(String message) {
        super(message);
    }

}
